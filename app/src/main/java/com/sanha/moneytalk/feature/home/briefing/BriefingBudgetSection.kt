package com.sanha.moneytalk.feature.home.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import java.text.NumberFormat

@Composable
internal fun BriefingBudgetSection(briefing: SpendingBriefing, onBudgetClick: (() -> Unit)?) {
    val numberFormat = NumberFormat.getNumberInstance()
    val remaining = briefing.budgetRemaining
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.home_briefing_budget_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (remaining == null) {
            Text(
                text = stringResource(
                    if (onBudgetClick == null) R.string.home_briefing_budget_missing_settings
                    else R.string.home_briefing_budget_missing
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = stringResource(
                    if (remaining < 0L) R.string.home_briefing_budget_over else R.string.home_briefing_budget_remaining,
                    numberFormat.format(if (remaining < 0L) -remaining else remaining)
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (remaining < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (briefing.isCurrentPeriod) {
                Text(
                    text = stringResource(R.string.home_briefing_remaining_days, briefing.remainingDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            briefing.dailyReference?.let { daily ->
                Text(
                    text = stringResource(R.string.home_briefing_daily_reference, numberFormat.format(daily)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        onBudgetClick?.let { action ->
            TextButton(onClick = action) {
                Text(stringResource(if (remaining == null) R.string.home_briefing_set_budget else R.string.home_briefing_edit_budget))
            }
        }
    }
}
