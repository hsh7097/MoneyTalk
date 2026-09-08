package com.sanha.moneytalk.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R

// 가이드 질문 목록 - 카테고리별 그룹핑
data class GuideQuestion(
    @StringRes val categoryRes: Int,
    @StringRes val questionRes: Int
)

val guideQuestions = listOf(
    // 지출 조회
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_coupang),
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_starbucks),
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_delivery),
    GuideQuestion(R.string.guide_category_expense_search, R.string.guide_q_expense_top_store),
    // 지출 분석
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_food),
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_category),
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_compare),
    GuideQuestion(R.string.guide_category_analysis, R.string.guide_q_analysis_saving_tip),
    // 정리/관리
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.guide_welcome),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.guide_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!hasApiKey) {
                    Text(
                        text = stringResource(R.string.guide_ai_service_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        groupedQuestions.forEach { (category, categoryQuestions) ->
            item(key = category) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column {
                            categoryQuestions.forEachIndexed { index, question ->
                                Surface(
                                    onClick = { onQuestionClick(question.question) },
                                    enabled = hasApiKey,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .padding(horizontal = 20.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = question.question,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (hasApiKey) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (index < categoryQuestions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
