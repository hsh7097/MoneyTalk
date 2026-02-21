package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

/**
 * 누적 곡선 차트에 표시할 단일 라인 데이터.
 *
 * 도메인 독립적: ExpenseEntity 등 도메인 모델을 참조하지 않음.
 *
 * @property points 일별 누적 금액 리스트 (index = dayOffset 0-based, value = 누적 금액)
 * @property color 차트 선 색상
 * @property isSolid true면 실선, false면 점선
 * @property label 범례 표시용 라벨
 */
@Immutable
data class CumulativeChartLine(
    val points: List<Long>,
    val color: Color,
    val isSolid: Boolean = false,
    val label: String
)

/**
 * 누적 곡선 차트 전체 데이터.
 *
 * X축 스트레칭 방식: 각 곡선은 자신의 포인트 수에 관계없이 차트 전체 폭을 채운다.
 * 28일 데이터도 31일 데이터도 동일한 폭으로 렌더링되어, "월 진행률 대비" 비교가 직관적.
 * X축 라벨은 daysInMonth(해당 월 말일) 기준으로 표시.
 *
 * @property lines 표시할 곡선 리스트
 * @property daysInMonth 해당 월 총 일수 (X축 라벨 기준)
 * @property todayDayIndex 오늘이 해당 월의 몇번째 날인지 (0-based, -1이면 과거 월)
 * @property yAxisMax Y축 고정 최대값. 0이면 lines 기반 자동 계산.
 *           토글 on/off 시 Y축 변동을 방지하려면 호출부에서 미리 계산하여 전달.
 */
@Immutable
data class CumulativeChartData(
    val lines: List<CumulativeChartLine>,
    val daysInMonth: Int,
    val todayDayIndex: Int = -1,
    val yAxisMax: Long = 0L
)

/** 차트 영역 내부 패딩 */
private val CHART_PADDING_START = 48.dp
private val CHART_PADDING_END = 16.dp
private val CHART_PADDING_TOP = 12.dp
private val CHART_PADDING_BOTTOM = 24.dp

/**
 * 도메인 독립적 누적 곡선 차트 Composable.
 *
 * Canvas 기반으로 외부 라이브러리 없이 구현.
 * 복수 곡선을 동시 렌더링하며, 각 곡선의 실선/점선 스타일을 CumulativeChartLine.isSolid로 결정.
 * 오늘 위치에 세로 점선 + 첫번째 곡선(이번 달)에 원형 마커 표시.
 *
 * 범례는 포함하지 않음 — 호출부에서 CumulativeChartLine.label과 color를 사용하여 구성.
 *
 * @param data 차트 데이터
 * @param modifier 외부 Modifier
 */
@Composable
fun CumulativeChartCompose(
    data: CumulativeChartData,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val labelColorArgb = remember(labelColor) { labelColor.toArgb() }

    // Y축 최대값: yAxisMax가 설정되면 사용, 아니면 lines 기반 자동 계산
    val yAxisCeil = remember(data) {
        if (data.yAxisMax > 0L) {
            data.yAxisMax
        } else {
            val rawMax = data.lines.maxOfOrNull { line ->
                line.points.maxOrNull() ?: 0L
            }?.coerceAtLeast(1L) ?: 1L
            ceilToNiceValue(rawMax)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 4.dp)
    ) {
        val paddingStart = CHART_PADDING_START.toPx()
        val paddingEnd = CHART_PADDING_END.toPx()
        val paddingTop = CHART_PADDING_TOP.toPx()
        val paddingBottom = CHART_PADDING_BOTTOM.toPx()

        val chartWidth = size.width - paddingStart - paddingEnd
        val chartHeight = size.height - paddingTop - paddingBottom

        if (chartWidth <= 0f || chartHeight <= 0f || data.daysInMonth <= 0) return@Canvas

        val yMax = yAxisCeil.toFloat()

        // ── 배경 가이드라인 (수평 점선) ──
        drawHorizontalGuides(
            paddingStart, paddingTop, chartWidth, chartHeight, yAxisCeil, numberFormat,
            labelColorArgb
        )

        // ── X축 라벨 (해당 월 말일 기준, 스트레칭 위치) ──
        drawXAxisLabels(
            paddingStart, paddingTop, chartHeight, chartWidth, data.daysInMonth,
            labelColorArgb
        )

        // ── 각 곡선 렌더링 (각 곡선이 전체 폭을 스트레칭) ──
        data.lines.forEach { line ->
            drawCumulativeLine(
                line = line,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                yMax = yMax,
                paddingStart = paddingStart,
                paddingTop = paddingTop,
                todayDayIndex = data.todayDayIndex
            )
        }

        // ── 오늘 위치 마커 (primaryLine 스트레칭 기준) ──
        val primaryPointCount = data.lines.firstOrNull()?.points?.size ?: 0
        if (data.todayDayIndex in 0 until primaryPointCount) {
            val todayRatio = data.todayDayIndex.toFloat() / (primaryPointCount - 1).coerceAtLeast(1)
            val todayX = paddingStart + todayRatio * chartWidth

            // 세로 점선
            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(todayX, paddingTop),
                end = Offset(todayX, paddingTop + chartHeight),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )

            // 첫번째 곡선 (이번 달)에 원형 마커
            val primaryLine = data.lines.firstOrNull()
            if (primaryLine != null && data.todayDayIndex < primaryLine.points.size) {
                val currentAmount = primaryLine.points[data.todayDayIndex]
                val markerY = paddingTop + chartHeight - (currentAmount / yMax) * chartHeight
                // 외곽 원 (흰색)
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(todayX, markerY)
                )
                // 내부 원 (Primary 색상)
                drawCircle(
                    color = primaryLine.color,
                    radius = 3.5.dp.toPx(),
                    center = Offset(todayX, markerY)
                )
            }
        }
    }
}

/**
 * 단일 누적 곡선 렌더링.
 *
 * 각 곡선은 자신의 포인트 수 기준으로 chartWidth 전체를 스트레칭하여 채운다.
 * 28일 데이터도 31일 데이터도 동일한 폭으로 렌더링되어 월 진행률 비교가 직관적.
 */
private fun DrawScope.drawCumulativeLine(
    line: CumulativeChartLine,
    chartWidth: Float,
    chartHeight: Float,
    yMax: Float,
    paddingStart: Float,
    paddingTop: Float,
    todayDayIndex: Int
) {
    if (line.points.isEmpty()) return

    // 이번 달 곡선(실선)은 오늘까지만 그리기
    val drawUpTo = if (line.isSolid && todayDayIndex >= 0) {
        minOf(line.points.size, todayDayIndex + 1)
    } else {
        line.points.size
    }

    if (drawUpTo <= 0) return

    // 각 곡선이 자기 포인트 수 기준으로 전체 폭을 스트레칭
    val totalPoints = line.points.size
    val xStep = if (totalPoints > 1) chartWidth / (totalPoints - 1) else 0f

    val path = Path().apply {
        for (i in 0 until drawUpTo) {
            val x = paddingStart + i * xStep
            val y = paddingTop + chartHeight - (line.points[i] / yMax) * chartHeight
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }

    val strokeStyle = if (line.isSolid) {
        Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    } else {
        Stroke(
            width = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )
    }

    drawPath(path = path, color = line.color, style = strokeStyle)
}

/**
 * 수평 가이드라인 + Y축 라벨
 */
@Suppress("LongParameterList")
private fun DrawScope.drawHorizontalGuides(
    paddingStart: Float,
    paddingTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    yAxisCeil: Long,
    numberFormat: NumberFormat,
    labelColorArgb: Int
) {
    val textPaint = android.graphics.Paint().apply {
        color = labelColorArgb
        textSize = 10.dp.toPx()
        isAntiAlias = true
    }

    // Y축 최대값 기준 균등 분할 (5등분: 0, 20%, 40%, 60%, 80%, 100%)
    val divisionCount = 5
    for (i in 0..divisionCount) {
        val ratio = i.toFloat() / divisionCount
        val y = paddingTop + chartHeight * (1f - ratio)
        val value = yAxisCeil * i / divisionCount

        // 수평 점선
        drawLine(
            color = Color.Gray.copy(alpha = 0.15f),
            start = Offset(paddingStart, y),
            end = Offset(paddingStart + chartWidth, y),
            strokeWidth = 0.5.dp.toPx()
        )

        // Y축 라벨
        val label = formatCompactAmount(value, numberFormat)
        drawContext.canvas.nativeCanvas.drawText(
            label,
            4.dp.toPx(),
            y + 4.dp.toPx(),
            textPaint
        )
    }
}

/**
 * X축 라벨 (해당 월 말일 기준, 스트레칭 위치)
 */
private fun DrawScope.drawXAxisLabels(
    paddingStart: Float,
    paddingTop: Float,
    chartHeight: Float,
    chartWidth: Float,
    daysInMonth: Int,
    labelColorArgb: Int
) {
    val textPaint = android.graphics.Paint().apply {
        color = labelColorArgb
        textSize = 10.dp.toPx()
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val labelY = paddingTop + chartHeight + 16.dp.toPx()

    // 6등분 라벨: 1일 ~ 말일을 균등 분할 (7개 라벨)
    // 예: 31일 → 1, 6, 11, 16, 21, 26, 31 / 28일 → 1, 6, 10, 15, 19, 24, 28
    val divisionCount = 6
    val labelDays = (0..divisionCount).map { i ->
        1 + ((daysInMonth - 1) * i.toFloat() / divisionCount).toInt()
    }

    // points: index 0=0원시작, 1=1일, ..., daysInMonth=말일 (총 daysInMonth+1개)
    // 곡선 xStep = chartWidth / daysInMonth → day번째 포인트의 x = day * xStep
    labelDays.forEach { day ->
        val ratio = day.toFloat() / daysInMonth.coerceAtLeast(1)
        drawContext.canvas.nativeCanvas.drawText(
            "$day",
            paddingStart + ratio * chartWidth,
            labelY,
            textPaint
        )
    }
}

/**
 * 금액을 간결하게 표시 (만원 단위)
 */
private fun formatCompactAmount(value: Long, numberFormat: NumberFormat): String {
    return when {
        value >= 10000L -> "${numberFormat.format(value / 10000)}만"
        value >= 1000L -> "${numberFormat.format(value / 1000)}천"
        else -> numberFormat.format(value)
    }
}

/**
 * 최대값을 보기 좋은 단위로 올림.
 *
 * - 100만 이하 → 100만 고정
 * - 100만 초과 → 200만 단위 올림
 *
 * 가능한 Y축: 100만, 200만, 400만, 600만, 800만, 1000만, 1200만, ...
 */
internal fun ceilToNiceValue(rawMax: Long): Long {
    if (rawMax <= 0L) return 1L
    val unit = 2_000_000L // 200만
    if (rawMax <= 1_000_000L) return 1_000_000L
    return ((rawMax + unit - 1) / unit) * unit
}
