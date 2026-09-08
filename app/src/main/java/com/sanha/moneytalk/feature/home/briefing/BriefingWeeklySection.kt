package com.sanha.moneytalk.feature.home.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

@Composable
internal fun BriefingWeeklySection(
    comparison: BriefingWeeklyComparison,
    isCurrentPeriod: Boolean,
    onEvidenceClick: (String?, Boolean) -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance()
    val difference = comparison.difference
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val recentColor = MaterialTheme.moneyTalkColors.expense
    val previousColor = if (isDark) Color(0xFF9EB6D8) else Color(0xFF48648E)
    val fontScale = LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val longestAmount = maxOf(
                numberFormat.format(comparison.recent.amount).length,
                numberFormat.format(comparison.previous.amount).length
            )
            if (maxWidth < 300.dp || fontScale > 1.2f || longestAmount > 9) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefingPeriodAmount(comparison.recent, true, recentColor, { onEvidenceClick(null, true) }, Modifier.fillMaxWidth())
                    BriefingPeriodAmount(comparison.previous, false, previousColor, { onEvidenceClick(null, false) }, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefingPeriodAmount(comparison.recent, true, recentColor, { onEvidenceClick(null, true) }, Modifier.weight(1f))
                    BriefingPeriodAmount(comparison.previous, false, previousColor, { onEvidenceClick(null, false) }, Modifier.weight(1f))
                }
            }
        }
        if (!isCurrentPeriod) {
            Text(
                text = stringResource(R.string.weekly_evidence_past_anchor),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            TextButton(onClick = { onEvidenceClick(increase.category, true) }) {
                Text(stringResource(R.string.weekly_evidence_open, increase.category))
            }
        }
    }
}

@Composable
private fun BriefingPeriodAmount(
    window: BriefingSpendingWindow,
    isRecent: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = DateTimeFormatter.ofPattern("M.d")
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(if (isRecent) "briefing-recent-evidence" else "briefing-previous-evidence")
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(if (isRecent) R.string.home_briefing_recent_week else R.string.weekly_evidence_previous),
            style = MaterialTheme.typography.labelLarge,
            color = accent
        )
        Text(
            text = stringResource(R.string.home_briefing_amount, NumberFormat.getNumberInstance().format(window.amount)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = stringResource(
                R.string.home_briefing_window_dates,
                dateFormat.format(window.startDate),
                dateFormat.format(window.endDateInclusive)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = accent
        )
    }
}
