package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import java.text.NumberFormat

/** 차트 아래에 두어 선택/드래그 중 plot의 위치는 유지한다. */
@Composable
internal fun CumulativeInspectionReadout(
    inspection: CumulativeChartInspection,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("chart-inspection-readout")
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.chart_inspection_date,
                    inspection.date.monthValue, inspection.date.dayOfMonth, inspection.dayIndex
                ),
                modifier = Modifier.weight(1f).testTag("chart-inspection-day").semantics {
                    stateDescription = inspection.dayIndex.toString()
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.chart_inspection_close)) }
        }
        inspection.values.forEachIndexed { index, value ->
            // 두 줄로 구성해 큰 글자에서도 긴 금액을 라벨과 같은 줄에 억지로 넣지 않는다.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(value.color, CircleShape))
                    Text(value.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = stringResource(R.string.common_won, NumberFormat.getNumberInstance().format(value.amount)),
                    modifier = Modifier.testTag("chart-inspection-value-$index"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.moneyTalkColors.expense
                )
            }
        }
        if (inspection.primaryUnavailable) {
            Text(
                stringResource(
                    if (inspection.primaryLimitedToToday) R.string.chart_inspection_primary_today_limit
                    else R.string.chart_inspection_primary_unavailable,
                    inspection.primaryLabel
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
