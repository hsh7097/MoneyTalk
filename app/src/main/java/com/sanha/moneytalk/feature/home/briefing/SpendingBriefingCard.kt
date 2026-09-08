package com.sanha.moneytalk.feature.home.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

/** 기록의 완전성을 단정하지 않는 로컬 브리핑. 조회와 화면 이동은 호출자가 담당한다. */
@Composable
fun SpendingBriefingCard(
    briefing: SpendingBriefing,
    isPartialCoverage: Boolean,
    hasSmsPermission: Boolean,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val comparison = briefing.weeklyComparison ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.home_briefing_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.home_briefing_recorded_basis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPartialCoverage || !hasSmsPermission) {
                Text(
                    text = stringResource(R.string.home_briefing_partial_records),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BriefingWeeklySection(comparison, briefing.isCurrentPeriod, onCategoryClick)
        }
    }
}
