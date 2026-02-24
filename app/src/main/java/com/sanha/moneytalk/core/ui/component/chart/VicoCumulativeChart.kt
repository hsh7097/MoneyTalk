package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer

/**
 * Vico 기반 누적 곡선 차트.
 *
 * Canvas 기반 [CumulativeChartCompose]를 대체하는 금융앱 스타일 차트.
 * 디자인 5대 포인트: 선 두께 3dp, 그리드 10% 투명도, 현재 위치 Dot,
 * 영역 아래 그라디언트, 숫자는 차트 위(타이틀 영역)에 배치.
 *
 * @param primaryLine 메인 곡선 (이번 달 누적)
 * @param comparisonLines 토글 활성화된 비교 곡선 리스트
 * @param daysInMonth 해당 월 총 일수
 * @param todayDayIndex 오늘이 해당 월의 몇번째 날인지 (0-based, -1이면 과거 월)
 * @param modifier 외부 Modifier
 */
@Composable
fun VicoCumulativeChart(
    primaryLine: CumulativeChartLine,
    comparisonLines: List<CumulativeChartLine>,
    daysInMonth: Int,
    todayDayIndex: Int,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // 데이터 변경 시 모델 업데이트
    LaunchedEffect(primaryLine, comparisonLines) {
        modelProducer.runTransaction {
            lineSeries {
                // 메인 곡선: 실선일 때 오늘까지만 표시
                val primaryPoints = if (todayDayIndex >= 0) {
                    primaryLine.points.take(todayDayIndex + 1)
                } else {
                    primaryLine.points
                }
                if (primaryPoints.isNotEmpty()) {
                    series(y = primaryPoints.map { it.toDouble() })
                }

                // 비교 곡선들
                comparisonLines.forEach { line ->
                    if (line.points.isNotEmpty()) {
                        series(y = line.points.map { it.toDouble() })
                    }
                }
            }
        }
    }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // 라인 스타일: 메인 곡선 (두꺼운 선 + 영역 그라디언트)
    val primaryVicoLine = rememberLine(
        fill = remember(primaryLine.color) {
            LineCartesianLayer.LineFill.single(fill(primaryLine.color))
        },
        areaFill = remember(primaryLine.color) {
            LineCartesianLayer.AreaFill.single(
                fill(primaryLine.color.copy(alpha = 0.15f))
            )
        },
    )

    // 비교 곡선 스타일들
    val comparisonVicoLines = comparisonLines.map { line ->
        rememberLine(
            fill = remember(line.color) {
                LineCartesianLayer.LineFill.single(fill(line.color))
            },
            areaFill = null,
        )
    }

    val allVicoLines = buildList {
        add(primaryVicoLine)
        addAll(comparisonVicoLines)
    }

    val lineProvider = LineCartesianLayer.LineProvider.series(allVicoLines)

    // X축 라벨 포매터
    val bottomValueFormatter = remember(daysInMonth) {
        CartesianValueFormatter { x, _, _ ->
            val day = x.toInt() + 1
            "$day"
        }
    }

    // Y축 라벨 포매터
    val startValueFormatter = remember {
        CartesianValueFormatter { y, _, _ ->
            formatCompactAmountSimple(y.toLong())
        }
    }

    val guidelineColor = onSurfaceColor.copy(alpha = 0.08f)
    val labelColor = onSurfaceColor.copy(alpha = 0.45f)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = lineProvider,
            ),
            startAxis = rememberStartAxis(
                label = rememberAxisLabelComponent(
                    color = labelColor,
                ),
                tick = null,
                guideline = rememberAxisGuidelineComponent(
                    color = guidelineColor,
                    thickness = 0.5.dp,
                ),
                line = null,
                valueFormatter = startValueFormatter,
            ),
            bottomAxis = rememberBottomAxis(
                label = rememberAxisLabelComponent(
                    color = labelColor,
                ),
                tick = null,
                guideline = null,
                line = null,
                valueFormatter = bottomValueFormatter,
            ),
        ),
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

/**
 * 금액을 간결하게 표시 (만원/천원 단위).
 */
private fun formatCompactAmountSimple(value: Long): String {
    return when {
        value >= 10_000L -> "${value / 10_000}만"
        value >= 1_000L -> "${value / 1_000}천"
        value > 0L -> "$value"
        else -> "0"
    }
}
