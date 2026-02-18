package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import java.text.NumberFormat
import java.util.Locale

/**
 * 도넛 차트 단일 조각 데이터
 *
 * @property category 카테고리 (이모지, 이름 등 접근용)
 * @property amount 해당 카테고리 지출 금액
 * @property percentage 전체 대비 비율 (0.0 ~ 1.0)
 * @property color 차트 arc 색상
 * @property displayLabel 범례 표시용 라벨 오버라이드 (null이면 "emoji displayName" 사용)
 */
@Immutable
data class DonutSlice(
    val category: Category,
    val amount: Int,
    val percentage: Float,
    val color: Color,
    val displayLabel: String? = null
) {
    /** 범례/접근성에서 사용할 표시 이름 */
    val label: String
        get() = displayLabel ?: "${category.emoji} ${category.displayName}"
}

/**
 * 카테고리별 지출 도넛 차트.
 *
 * Compose Canvas 기반으로 외부 라이브러리 없이 구현.
 * 가운데에 총 지출액, 하단에 범례(전달된 slices 전체)를 표시한다.
 *
 * @param slices 카테고리별 도넛 조각 리스트 (금액 내림차순)
 * @param totalAmount 전체 지출 합계
 * @param modifier 외부 Modifier
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DonutChartCompose(
    slices: List<DonutSlice>,
    totalAmount: Int,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    // 접근성 텍스트
    val accessibilityText = remember(slices, totalAmount) {
        "카테고리별 지출 차트. 총 지출 ${numberFormat.format(totalAmount)}원. " +
            slices.joinToString(", ") {
                "${it.label} ${(it.percentage * 100).toInt()}%"
            }
    }

    // 범례: slices 전체를 표시 (호출부에서 TOP3+그외 / 전체를 결정)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityText },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 도넛 차트 + 센터 텍스트
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val strokeWidth = 36.dp.toPx()
                val arcSize = Size(
                    width = size.width - strokeWidth,
                    height = size.height - strokeWidth
                )
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val gapDegrees = if (slices.size > 1) 2f else 0f

                var currentAngle = -90f

                slices.forEach { slice ->
                    val sweep = slice.percentage * 360f

                    if (sweep > 0f) {
                        drawArc(
                            color = slice.color,
                            startAngle = currentAngle + gapDegrees / 2,
                            sweepAngle = (sweep - gapDegrees).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )
                    }
                    currentAngle += sweep
                }
            }

            // 센터 텍스트: 총 지출액
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.home_total_expense_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "₩${numberFormat.format(totalAmount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 범례: slices 전체 (호출부에서 TOP3+그외 또는 전체를 결정)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            slices.forEach { slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(slice.color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${slice.label} ${(slice.percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
