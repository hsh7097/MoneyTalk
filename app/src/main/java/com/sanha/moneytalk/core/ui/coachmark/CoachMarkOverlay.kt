package com.sanha.moneytalk.core.ui.coachmark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.animateSizeAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import kotlin.math.roundToInt

private val SCRIM_COLOR = Color.Black.copy(alpha = 0.85f)
private val SPOTLIGHT_BORDER_COLOR = Color.White.copy(alpha = 0.5f)
private val SPOTLIGHT_BORDER_WIDTH = 2.dp
private val TOOLTIP_MARGIN = 16.dp
private val TOOLTIP_HORIZONTAL_PADDING = 20.dp

/**
 * 코치마크 오버레이.
 * Canvas scrim + 타겟 영역 스포트라이트 컷아웃 + 설명 툴팁 카드.
 *
 * @param state 코치마크 상태 (스텝, 표시 여부)
 * @param targetRegistry 타겟 요소 좌표 레지스트리
 * @param onComplete 모든 스텝 완료 또는 건너뛰기 시 호출
 */
@Composable
fun CoachMarkOverlay(
    state: CoachMarkState,
    targetRegistry: CoachMarkTargetRegistry,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(animationSpec = spring()),
        exit = fadeOut(animationSpec = spring()),
        modifier = modifier
    ) {
        val step = state.currentStep ?: return@AnimatedVisibility
        val targetRect = targetRegistry.targets[step.targetKey]

        val density = LocalDensity.current
        val spotlightPaddingPx = with(density) { step.spotlightPadding.toPx() }
        val cornerRadiusPx = with(density) { step.spotlightCornerRadius.toPx() }
        val borderWidthPx = with(density) { SPOTLIGHT_BORDER_WIDTH.toPx() }

        // 오버레이 Box의 루트 오프셋 (좌표계 변환용)
        var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

        // 타겟 좌표를 오버레이 로컬 좌표로 변환
        val localRect = targetRect?.let { rect ->
            Rect(
                left = rect.left - overlayOrigin.x,
                top = rect.top - overlayOrigin.y,
                right = rect.right - overlayOrigin.x,
                bottom = rect.bottom - overlayOrigin.y
            )
        }

        // 스포트라이트 영역 (애니메이션)
        val animatedOffset by animateOffsetAsState(
            targetValue = localRect?.let {
                Offset(it.left - spotlightPaddingPx, it.top - spotlightPaddingPx)
            } ?: Offset.Zero,
            animationSpec = spring(),
            label = "spotlightOffset"
        )
        val animatedSize by animateSizeAsState(
            targetValue = localRect?.let {
                Size(
                    it.width + spotlightPaddingPx * 2,
                    it.height + spotlightPaddingPx * 2
                )
            } ?: Size.Zero,
            animationSpec = spring(),
            label = "spotlightSize"
        )

        // 전체 컨테이너 높이 (툴팁 위치 계산용)
        var containerHeight by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    containerHeight = it.size.height.toFloat()
                    val bounds = it.boundsInRoot()
                    overlayOrigin = Offset(bounds.left, bounds.top)
                }
                // 터치 이벤트 소비 (하위로 전달 안 함)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                            // 모든 터치 소비
                        }
                    }
                }
        ) {
            // 1. Scrim + 스포트라이트 컷아웃
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawBehind {
                        // 전체 scrim
                        drawRect(color = SCRIM_COLOR)

                        // 타겟 영역 투명 컷아웃
                        if (localRect != null) {
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = animatedOffset,
                                size = animatedSize,
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                blendMode = BlendMode.Clear
                            )
                            // 컷아웃 테두리 (다크테마 대비용)
                            drawRoundRect(
                                color = SPOTLIGHT_BORDER_COLOR,
                                topLeft = animatedOffset,
                                size = animatedSize,
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                style = Stroke(width = borderWidthPx)
                            )
                        }
                    }
            )

            // 2. 툴팁 카드
            if (localRect != null) {
                val tooltipMarginPx = with(density) { TOOLTIP_MARGIN.toPx() }

                // 툴팁 높이 측정용
                var tooltipHeight by remember { mutableStateOf(0f) }

                val belowY = localRect.bottom + spotlightPaddingPx + tooltipMarginPx
                val aboveY = localRect.top - spotlightPaddingPx - tooltipHeight - tooltipMarginPx

                // 선호 위치 계산 후, 화면 밖이면 반대쪽으로 자동 뒤집기
                val tooltipY = when (step.tooltipPosition) {
                    TooltipPosition.BELOW ->
                        if (belowY + tooltipHeight <= containerHeight) belowY else aboveY
                    TooltipPosition.ABOVE ->
                        if (aboveY >= 0f) aboveY else belowY
                }

                // 화면 밖으로 나가지 않도록 최종 클램핑
                val maxY = (containerHeight - tooltipHeight - tooltipMarginPx)
                    .coerceAtLeast(tooltipMarginPx)
                val clampedY = tooltipY.coerceIn(tooltipMarginPx, maxY)

                val animatedY by animateFloatAsState(
                    targetValue = clampedY,
                    animationSpec = spring(),
                    label = "tooltipY"
                )

                TooltipCard(
                    step = step,
                    currentIndex = state.currentStepIndex,
                    totalSteps = state.totalSteps,
                    isLastStep = state.isLastStep,
                    onNext = { state.next() },
                    onSkip = {
                        state.dismiss()
                        onComplete()
                    },
                    onFinish = {
                        state.dismiss()
                        onComplete()
                    },
                    modifier = Modifier
                        .offset { IntOffset(0, animatedY.roundToInt()) }
                        .padding(horizontal = TOOLTIP_HORIZONTAL_PADDING)
                        .fillMaxWidth()
                        .onGloballyPositioned { tooltipHeight = it.size.height.toFloat() }
                )
            }
        }
    }
}

@Composable
private fun TooltipCard(
    step: CoachMarkStep,
    currentIndex: Int,
    totalSteps: Int,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 스텝 인디케이터
            Text(
                text = stringResource(
                    R.string.coach_mark_step_format,
                    currentIndex + 1,
                    totalSteps
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 제목
            Text(
                text = stringResource(step.titleResId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 설명
            Text(
                text = stringResource(step.descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 버튼 Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 건너뛰기 (마지막 스텝이 아닐 때만)
                if (!isLastStep) {
                    TextButton(onClick = onSkip) {
                        Text(
                            text = stringResource(R.string.coach_mark_skip),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // 다음 / 완료
                FilledTonalButton(
                    onClick = if (isLastStep) onFinish else onNext
                ) {
                    Text(
                        text = if (isLastStep) {
                            stringResource(R.string.coach_mark_finish)
                        } else {
                            stringResource(R.string.coach_mark_next)
                        }
                    )
                }
            }
        }
    }
}
