package com.sanha.moneytalk.feature.weeklyevidence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardInfo
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import com.sanha.moneytalk.feature.transactionactions.ui.TransactionQuickActionDialog
import com.sanha.moneytalk.feature.transactionactions.ui.TransactionQuickActionViewModel
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import java.text.NumberFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun WeeklyEvidenceScreen(
    onBack: () -> Unit,
    viewModel: WeeklyEvidenceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val quickActions: TransactionQuickActionViewModel = hiltViewModel(key = "WeeklyEvidenceQuickActions")
    TransactionQuickActionDialog(quickActions)
    WeeklyEvidenceContent(
        state = state,
        onBack = onBack,
        onPeriodSelected = viewModel::selectPeriod,
        onRetry = viewModel::reload,
        onExpenseClick = { TransactionEditActivity.open(context, expenseId = it) },
        onExpenseLongClick = { quickActions.open(TransactionTarget.Expense(it)) }
    )
}

/** DB/Activity 없이도 기간 전환, 합계와 거래 동작을 검증할 수 있는 화면 본문. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyEvidenceContent(
    state: WeeklyEvidenceUiState,
    onBack: () -> Unit,
    onPeriodSelected: (WeeklyEvidencePeriod) -> Unit,
    onRetry: () -> Unit,
    onExpenseClick: (Long) -> Unit,
    onExpenseLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val request = state.request
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        TopAppBar(
            title = {
                Text(
                    request?.category?.let { stringResource(R.string.weekly_evidence_title, it) }
                        ?: stringResource(R.string.weekly_evidence_all_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        if (request != null) {
            TabRow(selectedTabIndex = state.selectedPeriod.ordinal) {
                WeeklyEvidencePeriod.entries.forEach { period ->
                    Tab(
                        selected = state.selectedPeriod == period,
                        onClick = { onPeriodSelected(period) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("weekly-period-${period.name}"),
                        text = {
                            Text(stringResource(
                                if (period == WeeklyEvidencePeriod.RECENT) R.string.home_briefing_recent_week
                                else R.string.weekly_evidence_previous
                            ))
                        }
                    )
                }
            }
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.hasError || request == null -> Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.weekly_evidence_error))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.weekly_evidence_retry)) }
            }
            else -> {
                val recent = state.selectedPeriod == WeeklyEvidencePeriod.RECENT
                val records = if (recent) state.records.recent else state.records.previous
                val amount = if (recent) state.records.recentAmount else state.records.previousAmount
                val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                val start = if (recent) request.recentStart else request.previousStart
                val end = if (recent) request.recentEndInclusive else request.previousEndInclusive
                val grouped = if (recent) state.records.recentByDate else state.records.previousByDate
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("weekly-evidence-list"),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.home_briefing_window_dates, dateFormat.format(start), dateFormat.format(end)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                stringResource(R.string.home_briefing_amount, NumberFormat.getNumberInstance().format(amount)),
                                modifier = Modifier.testTag("weekly-evidence-total"),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.moneyTalkColors.expense,
                                fontWeight = FontWeight.Bold
                            )
                            Text(stringResource(R.string.weekly_evidence_count, records.size), style = MaterialTheme.typography.bodySmall)
                            Text(
                                stringResource(R.string.weekly_evidence_basis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (records.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.weekly_evidence_empty),
                                modifier = Modifier.padding(vertical = 24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    grouped.forEach { (date, expenses) ->
                        item(key = "date-$date") {
                            Text(
                                dateFormat.format(date),
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        items(expenses, key = { "expense-${it.id}" }) { expense ->
                            val info = object : TransactionCardInfo by ExpenseTransactionCardInfo(expense) {
                                override val time: String = DateTimeFormatter.ofPattern("HH:mm")
                                    .format(Instant.ofEpochMilli(expense.dateTime).atZone(request.zoneId))
                            }
                            TransactionCardCompose(
                                info = info,
                                onClick = { onExpenseClick(expense.id) },
                                onLongClick = { onExpenseLongClick(expense.id) },
                                modifier = Modifier.fillMaxWidth().testTag("weekly-expense-${expense.id}")
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(
                                R.string.weekly_evidence_cutoff,
                                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                                    .format(Instant.ofEpochMilli(request.asOfMillis).atZone(request.zoneId))
                            ),
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
