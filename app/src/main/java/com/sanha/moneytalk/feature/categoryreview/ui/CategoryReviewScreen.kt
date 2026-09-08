package com.sanha.moneytalk.feature.categoryreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.feature.transactionedit.ui.TransactionEditActivity
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CategoryReviewScreen(
    onBack: () -> Unit,
    viewModel: CategoryReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CategoryReviewContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onExpenseClick = { expenseId -> TransactionEditActivity.open(context, expenseId = expenseId) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryReviewContent(
    state: CategoryReviewUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onExpenseClick: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_review_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                CategoryReviewUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().testTag("category_review_loading"),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                CategoryReviewUiState.Error -> CategoryReviewMessage(
                    message = stringResource(R.string.category_review_error),
                    onRetry = onRetry
                )
                is CategoryReviewUiState.Content -> if (state.count == 0) {
                    CategoryReviewMessage(message = stringResource(R.string.category_review_empty))
                } else {
                    CategoryReviewList(state, onExpenseClick)
                }
            }
        }
    }
}

@Composable
private fun CategoryReviewMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.category_review_retry)) }
        }
    }
}

@Composable
private fun CategoryReviewList(state: CategoryReviewUiState.Content, onExpenseClick: (Long) -> Unit) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("category_review_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text(stringResource(R.string.category_review_count, state.count), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.category_review_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.days.forEach { day ->
            item(key = "date_${day.date}") {
                Text(
                    text = dateFormat.format(day.date),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(day.expenses, key = { "expense_${it.id}" }) { expense ->
                TransactionCardCompose(
                    info = ExpenseTransactionCardInfo(expense),
                    modifier = Modifier.fillMaxWidth().testTag("category_review_expense_${expense.id}"),
                    onClick = { onExpenseClick(expense.id) }
                )
            }
        }
    }
}
