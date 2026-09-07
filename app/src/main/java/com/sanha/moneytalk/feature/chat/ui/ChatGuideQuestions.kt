package com.sanha.moneytalk.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
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
                            text = stringResource(R.string.guide_ai_service_unavailable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    groupedQuestions.entries.forEachIndexed { index, (category, categoryQuestions) ->
                        Text(
                            text = "${categoryEmojis[category] ?: "\uD83D\uDCAC"} $category",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        categoryQuestions.forEach { resolvedQuestion ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
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
                                        vertical = 8.dp
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

                        if (index < groupedQuestions.size - 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}
