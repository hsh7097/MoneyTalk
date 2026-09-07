package com.sanha.moneytalk.feature.chat.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

private val BounceInterpolator = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** 타이핑 인디케이터. AI 응답 생성 중임을 점 애니메이션으로 표시 */
@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    // 3개 도트 각각의 오프셋 애니메이션 (시차를 두고 튀어오르기)
    val dotOffsets = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0f at 0
                    -8f at 200 + (index * 150) using FastOutSlowInEasing
                    0f at 400 + (index * 150) using BounceInterpolator
                    0f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot$index"
        )
    }

    val dotAlphas = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0.4f at 0
                    1f at 200 + (index * 150)
                    0.4f at 400 + (index * 150)
                    0.4f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha$index"
        )
    }

    val dotScales = List(3) { index ->
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    1f at 0
                    1.3f at 200 + (index * 150)
                    1f at 400 + (index * 150)
                    1f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "scale$index"
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .size((10 * dotScales[index].value).dp)
                        .offset { IntOffset(0, dotOffsets[index].value.dp.roundToPx()) }
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = dotAlphas[index].value
                            )
                        )
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.chat_thinking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
