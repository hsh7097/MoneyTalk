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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
 * 도메인 독립적 누적 추이 섹션.
 *
 * 고정 곡선(항상 표시) + 토글 가능 곡선(원형 토글) + 차트 + 범례를 조합.
 * Canvas 렌더링은 [CumulativeChartCompose]에 위임.
 *
 * 레이아웃 구조:
 * - 제목
 * - 차트 (CumulativeChartCompose)
 * - 범례 행 (primaryLine: 채워진 원 / toggleableLines: 테두리 원 ↔ 채워진 원 토글)
 *
 * 사용 예:
 * - 홈: 전체 지출 누적 (primaryLine) + 전월/3개월평균/예산 (toggleableLines)
 * - 카테고리: 카테고리별 누적 (primaryLine) + 전월 해당 카테고리 (toggleableLines)
 *
 * @param title 섹션 제목 (예: "지출 추이", "식비 추이")
 * @param primaryLine 항상 표시되는 메인 곡선 (이번 달 누적)
 * @param toggleableLines 토글 가능한 비교 곡선 리스트
 * @param daysInMonth 해당 월 총 일수
 * @param todayDayIndex 오늘이 해당 월의 몇번째 날인지 (0-based, -1이면 과거 월)
 * @param modifier 외부 Modifier
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
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 차트
        CumulativeChartCompose(data = chartData)

        Spacer(modifier = Modifier.height(8.dp))

        // 범례 행: primaryLine(채워진 원) + toggleableLines(테두리/채워진 원 토글)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 메인 곡선 (항상 표시, 채워진 큰 원)
            LegendItem(
                color = primaryLine.color,
                label = primaryLine.label,
                filled = true,
                toggleable = false
            )
            Spacer(modifier = Modifier.width(12.dp))

            // 토글 가능 곡선 (테두리 원 ↔ 채워진 원)
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
