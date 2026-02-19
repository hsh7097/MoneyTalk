package com.sanha.moneytalk.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.util.DateUtils

// 가이드 질문 목록 - 카테고리별 그룹핑
data class GuideQuestion(
    @StringRes val categoryRes: Int,
    @StringRes val questionRes: Int
)

val guideQuestions = listOf(
    // 지출 분석
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_food),
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_category),
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_compare),
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_saving_tip),
    // 지출 조회
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_coupang),
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_starbucks),
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_delivery),
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_top_store),
    // 카테고리 관리
    GuideQuestion(R.string.guide_category_manage, R.string.guide_q_manage_coupang),
    GuideQuestion(R.string.guide_category_manage, R.string.guide_q_manage_baemin),
    GuideQuestion(R.string.guide_category_manage, R.string.guide_q_manage_uncategorized)
)

/** 가이드 질문 오버레이. 빈 채팅방에서 예시 질문 칩을 표시하여 대화 시작을 유도 */
@Composable
fun GuideQuestionsOverlay(
    questions: List<GuideQuestion>,
    hasApiKey: Boolean,
    onQuestionClick: (String) -> Unit
) {
    val categoryExpenseSearch = stringResource(R.string.guide_category_expense_search)
    val categoryAnalysis = stringResource(R.string.guide_category_analysis)
    val categoryManage = stringResource(R.string.guide_category_manage)

    val categoryEmojis = mapOf(
        categoryAnalysis to "\uD83D\uDCCA",
        categoryExpenseSearch to "\uD83D\uDD0D",
        categoryManage to "\uD83C\uDFF7\uFE0F"
    )

    // 질문들을 카테고리 문자열로 그룹핑
    data class ResolvedQuestion(val category: String, val question: String)

    val resolvedQuestions = questions.map { q ->
        ResolvedQuestion(
            category = stringResource(q.categoryRes),
            question = stringResource(q.questionRes)
        )
    }
    val groupedQuestions = resolvedQuestions.groupBy { it.category }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.guide_welcome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.guide_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!hasApiKey) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.guide_api_key_required),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    groupedQuestions.forEach { (category, categoryQuestions) ->
                        Text(
                            text = "${categoryEmojis[category] ?: "\uD83D\uDCAC"} $category",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        categoryQuestions.forEach { resolvedQuestion ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable(enabled = hasApiKey) {
                                        onQuestionClick(resolvedQuestion.question)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = resolvedQuestion.question,
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp,
                                        vertical = 10.dp
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (hasApiKey) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

/** 채팅 메시지 버블. 사용자/AI 메시지를 좌우 정렬하고 마크다운 렌더링 지원 */
@Composable
fun ChatBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }

        Text(
            text = DateUtils.formatTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}

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
                        .offset(y = dotOffsets[index].value.dp)
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

/** 재시도 버튼. AI 응답 실패 시 다시 시도할 수 있는 버튼을 표시 */
@Composable
fun RetryButton(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.chat_retry),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** API 키 입력 다이얼로그. 채팅 시작 시 Gemini API 키가 없으면 표시 */
@Composable
fun ApiKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_api_key_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dialog_api_key_message),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.dialog_api_key_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_api_key_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(apiKey) },
                enabled = apiKey.isNotBlank()
            ) {
                Text(stringResource(R.string.dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
