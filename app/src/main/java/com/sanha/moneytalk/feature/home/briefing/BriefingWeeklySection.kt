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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun BriefingWeeklySection(
    comparison: BriefingWeeklyComparison,
    isCurrentPeriod: Boolean,
    onCategoryClick: (String) -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    val difference = comparison.difference
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(
                if (isCurrentPeriod) R.string.home_briefing_recent_week else R.string.home_briefing_period_last_week
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.home_briefing_amount, numberFormat.format(comparison.recent.amount)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(
                R.string.home_briefing_window_dates,
                dateFormat.format(comparison.recent.startDate),
                dateFormat.format(comparison.recent.endDateInclusive)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.home_briefing_previous_week,
                numberFormat.format(comparison.previous.amount),
                dateFormat.format(comparison.previous.startDate),
                dateFormat.format(comparison.previous.endDateInclusive)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val changeText = when {
            comparison.recent.transactionCount == 0 && comparison.previous.transactionCount == 0 ->
                stringResource(R.string.home_briefing_no_weekly_records)
            difference > 0L -> stringResource(R.string.home_briefing_week_increased, numberFormat.format(difference))
            difference < 0L -> stringResource(R.string.home_briefing_week_decreased, numberFormat.format(-difference))
            else -> stringResource(R.string.home_briefing_week_same)
        }
        Text(text = changeText, style = MaterialTheme.typography.bodyMedium)
        comparison.largestCategoryIncrease?.let { increase ->
            Text(
                text = stringResource(
                    R.string.home_briefing_category_increase,
                    increase.category,
                    numberFormat.format(increase.increase)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { onCategoryClick(increase.category) }) {
                Text(stringResource(R.string.home_briefing_category_details, increase.category))
            }
        }
    }
}
