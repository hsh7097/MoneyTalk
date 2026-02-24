package com.sanha.moneytalk.core.ui.component.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.theme.moneyTalkTypography
import java.text.NumberFormat
import java.util.Locale

/**
 * 토글 가능한 곡선 정보.
 *
 * checked 상태는 [CumulativeTrendSection] 내부 remember로 관리.
 *
 * @property line 곡선 데이터 (색상, 포인트, 라벨 등)
 * @property initialChecked 초기 체크 상태 (기본 true)
 */
@Immutable
data class ToggleableLine(
    val line: CumulativeChartLine,
    val initialChecked: Boolean = true
)

/**
 * 금융앱 스타일 누적 추이 섹션 (Vico 차트).
 *
 * 레이아웃 구조:
 * - 타이틀 (예: "이번 달 누적 지출")
 * - 큰 금액 (₩1,240,000)
 * - 비교 문구 (지난달 대비 12% 더 쓰고 있어요)
 * - Vico 차트
 * - 범례 행 (토글 가능)
 *
 * @param info 차트 데이터 Contract ([SpendingTrendInfo] 구현체)
 * @param modifier 외부 Modifier
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CumulativeTrendSection(
    info: SpendingTrendInfo,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    // 각 토글 곡선의 체크 상태 관리
    val toggleStates = remember(info.toggleableLines.size) {
        info.toggleableLines.map { mutableStateOf(it.initialChecked) }
    }

    // 각 비교 곡선의 알파 애니메이션 (OFF: 데이터→0 후 페이드아웃, ON: 즉시 표시 후 데이터 상승)
    val lineAlphas = remember(info.toggleableLines.size) {
        info.toggleableLines.map { Animatable(if (it.initialChecked) 1f else 0f) }
    }

    toggleStates.forEachIndexed { index, state ->
        LaunchedEffect(state.value) {
            if (index >= lineAlphas.size) return@LaunchedEffect
            if (state.value) {
                // Toggle ON: 즉시 보이게 → Vico가 0→실제값 애니메이션
                lineAlphas[index].snapTo(1f)
            } else {
                // Toggle OFF: Vico가 실제값→0 애니메이션 완료 대기 → 페이드아웃
                delay(350L)
                lineAlphas[index].animateTo(0f, tween(150))
            }
        }
    }

    // 모든 비교 곡선 유지 (비활성 곡선은 데이터 0 → Vico가 y=0으로 애니메이션)
    // 시리즈 개수를 일정하게 유지하여 Vico diff 애니메이션이 동작하도록 함
    val allComparisonLines = remember(
        info.toggleableLines, toggleStates.map { it.value }
    ) {
        info.toggleableLines.mapIndexed { index, toggleableLine ->
            val isActive = index < toggleStates.size && toggleStates[index].value
            if (isActive) {
                toggleableLine.line
            } else {
                toggleableLine.line.copy(
                    points = List(toggleableLine.line.points.size) { 0L }
                )
            }
        }
    }

    // Y축 최대값: 토글 상태와 무관하게 모든 곡선의 최대값 기준 고정
    val yAxisMax = remember(info.primaryLine, info.toggleableLines) {
        val allMax = maxOf(
            info.primaryLine.points.maxOrNull() ?: 0L,
            info.toggleableLines.maxOfOrNull { it.line.points.maxOrNull() ?: 0L } ?: 0L
        )
        ceilToNiceValue(allMax)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 타이틀
        Text(
            text = info.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.moneyTalkColors.textSecondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 큰 금액
        Text(
            text = "₩${numberFormat.format(info.currentAmount)}",
            style = MaterialTheme.moneyTalkTypography.numberLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 비교 문구
        if (info.comparisonText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            val comparisonColor = when (info.isOverSpending) {
                true -> MaterialTheme.colorScheme.error
                false -> MaterialTheme.moneyTalkColors.income
                null -> MaterialTheme.moneyTalkColors.textTertiary
            }
            Text(
                text = info.comparisonText,
                style = MaterialTheme.typography.bodySmall,
                color = comparisonColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vico 차트
        VicoCumulativeChart(
            primaryLine = info.primaryLine,
            comparisonLines = allComparisonLines,
            comparisonAlphas = lineAlphas.map { it.value },
            daysInMonth = info.daysInMonth,
            todayDayIndex = info.todayDayIndex,
            yAxisMax = yAxisMax,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 범례 행: primaryLine(채워진 원) + toggleableLines(테두리/채워진 원 토글)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 메인 곡선 (항상 표시, 채워진 큰 원)
            LegendItem(
                color = info.primaryLine.color,
                label = info.primaryLine.label,
                filled = true,
                toggleable = false
            )
            Spacer(modifier = Modifier.width(12.dp))

            // 토글 가능 곡선 (테두리 원 ↔ 채워진 원)
            info.toggleableLines.forEachIndexed { index, toggleable ->
                if (index < toggleStates.size) {
                    val isChecked = toggleStates[index].value
                    LegendItem(
                        color = toggleable.line.color,
                        label = toggleable.line.label,
                        filled = isChecked,
                        toggleable = true,
                        onClick = { toggleStates[index].value = !toggleStates[index].value }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}

/**
 * 하위 호환용 오버로드 (개별 파라미터 버전).
 *
 * CategoryDetail 등 기존 호출부를 위해 Canvas 기반 차트를 유지.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CumulativeTrendSection(
    title: String,
    primaryLine: CumulativeChartLine,
    toggleableLines: List<ToggleableLine>,
    daysInMonth: Int,
    todayDayIndex: Int,
    modifier: Modifier = Modifier
) {
    // 각 토글 곡선의 체크 상태 관리
    val toggleStates = remember(toggleableLines.size) {
        toggleableLines.map { mutableStateOf(it.initialChecked) }
    }

    // Y축 최대값: 토글 상태와 무관하게 모든 곡선의 최대값 기준으로 고정
    val yAxisMax = remember(primaryLine, toggleableLines) {
        val allMax = maxOf(
            primaryLine.points.maxOrNull() ?: 0L,
            toggleableLines.maxOfOrNull { it.line.points.maxOrNull() ?: 0L } ?: 0L
        )
        ceilToNiceValue(allMax)
    }

    // 차트 데이터 동적 구성 (X축 스트레칭, Y축 고정)
    val chartData = remember(
        primaryLine, toggleableLines, daysInMonth, todayDayIndex,
        yAxisMax, toggleStates.map { it.value }
    ) {
        val lines = mutableListOf(primaryLine)

        toggleableLines.forEachIndexed { index, toggleable ->
            if (index < toggleStates.size && toggleStates[index].value) {
                lines.add(toggleable.line)
            }
        }

        CumulativeChartData(
            lines = lines,
            daysInMonth = daysInMonth,
            todayDayIndex = todayDayIndex,
            yAxisMax = yAxisMax
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 제목
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Canvas 기반 차트 (하위 호환)
        CumulativeChartCompose(data = chartData)

        Spacer(modifier = Modifier.height(8.dp))

        // 범례 행
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LegendItem(
                color = primaryLine.color,
                label = primaryLine.label,
                filled = true,
                toggleable = false
            )
            Spacer(modifier = Modifier.width(12.dp))

            toggleableLines.forEachIndexed { index, toggleable ->
                if (index < toggleStates.size) {
                    val isChecked = toggleStates[index].value
                    LegendItem(
                        color = toggleable.line.color,
                        label = toggleable.line.label,
                        filled = isChecked,
                        toggleable = true,
                        onClick = { toggleStates[index].value = !toggleStates[index].value }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
            }
        }
    }
}

/**
 * 범례 아이템.
 *
 * - [filled] = true → 색상으로 채워진 원 (활성)
 * - [filled] = false → 테두리만 있는 원 (비활성)
 * - [toggleable] = true → 클릭 가능, 글자 크기와 동일한 원
 * - [toggleable] = false → 클릭 불가 (primaryLine), 글자 크기와 동일한 채워진 원
 */
@Composable
private fun LegendItem(
    color: Color,
    label: String,
    filled: Boolean,
    toggleable: Boolean,
    onClick: (() -> Unit)? = null
) {
    val textAlpha = if (filled) 0.7f else 0.35f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .then(
                if (toggleable && onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        // 원: 글자 크기에 맞춘 크기 (12dp)
        val dotSize = 12.dp
        val borderWidth = 1.5.dp
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier.background(color)
                    } else {
                        Modifier.border(borderWidth, color.copy(alpha = 0.6f), CircleShape)
                    }
                )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
        )
    }
}
