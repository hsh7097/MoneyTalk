package com.sanha.moneytalk.feature.home.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** 예상 지출 상위 3건과 전체 목록. 클릭하면 예측의 근거인 실제 거래 ID를 전달한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpenseForecastCard(
    forecast: RecurringExpenseForecast,
    onTransactionClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (forecast.items.isEmpty()) return
    var showAll by remember(forecast.asOfDate) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecurringExpenseHeader(forecast)
            forecast.items.take(3).forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                RecurringExpenseRow(item, onTransactionClick)
            }
            Text(
                text = stringResource(R.string.home_recurring_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (forecast.items.size > 3) {
                TextButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_recurring_show_all, forecast.items.size))
                }
            }
        }
    }

    if (showAll) {
        ModalBottomSheet(
            onDismissRequest = { showAll = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecurringExpenseHeader(forecast)
                Text(
                    text = stringResource(R.string.home_recurring_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(forecast.items, key = { it.sourceExpenseId }) { item ->
                    RecurringExpenseRow(item) { expenseId ->
                        showAll = false
                        onTransactionClick(expenseId)
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                }
            }
            TextButton(onClick = { showAll = false }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

@Composable
private fun RecurringExpenseHeader(forecast: RecurringExpenseForecast) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.home_recurring_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(
                R.string.home_recurring_period,
                recurringDate(forecast.asOfDate),
                recurringDate(forecast.untilDate)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.home_recurring_total, recurringAmount(forecast.totalExpectedAmount)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.moneyTalkColors.expense
        )
    }
}

@Composable
private fun RecurringExpenseRow(
    item: RecurringExpenseForecastItem,
    onTransactionClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onTransactionClick(item.sourceExpenseId) }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.storeName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.home_recurring_card_category, item.cardName, item.category),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.home_recurring_expected_date, recurringDate(item.expectedDate)),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = recurringAmount(item.expectedAmount),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.moneyTalkColors.expense
            )
        }
        Text(
            text = stringResource(R.string.home_recurring_evidence, item.observedMonthCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun recurringDate(date: LocalDate): String =
    stringResource(R.string.home_recurring_date, date.monthValue, date.dayOfMonth)

@Composable
private fun recurringAmount(amount: Long): String = stringResource(
    R.string.home_recurring_amount,
    NumberFormat.getNumberInstance(Locale.getDefault()).format(amount)
)
