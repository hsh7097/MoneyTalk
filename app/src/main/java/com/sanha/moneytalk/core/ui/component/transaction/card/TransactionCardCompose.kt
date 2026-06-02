package com.sanha.moneytalk.core.ui.component.transaction.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.getCustomCategoryBackgroundColor
import com.sanha.moneytalk.core.ui.component.rememberCategoryEmoji
import com.sanha.moneytalk.core.util.toDpTextUnit
import java.text.NumberFormat
import java.util.Locale

/**
 * 지출/수입 통합 거래 카드 Composable.
 * Info를 받아 순수 렌더링만 수행한다. 클릭은 onClick으로 상위에 위임.
 *
 * 디자인: 카드 형태 (12dp radius, border, shadow)
 * - 좌측: 카테고리 아이콘 (원형 배경)
 * - 중앙: 가게명 + [카테고리 칩] 시간 • 카드명
 * - 우측: -금액원
 */
@Composable
fun TransactionCardCompose(
    info: TransactionCardInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val resolvedCategoryEmoji = rememberCategoryEmoji(info.categoryTag.orEmpty())
    val amountPrefix = if (info.isIncome) "+" else "-"
    val formattedAmount =
        "${amountPrefix}${stringResource(R.string.common_won, numberFormat.format(info.amount))}"
    val isStatsExcluded = info.isExcludedFromStats
    val cardContainer = if (isStatsExcluded) {
        MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (FriendlyMoneyColors.isDark) 0.58f else 0.48f
        )
    } else {
        MaterialTheme.colorScheme.surface
    }
    val cardBorder = if (isStatsExcluded) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentPrimary = if (isStatsExcluded) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        FriendlyMoneyColors.textPrimary
    }
    val contentSecondary = if (isStatsExcluded) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    } else {
        FriendlyMoneyColors.textSecondary
    }
    val tagContainer = if (isStatsExcluded) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)
    } else {
        FriendlyMoneyColors.mintTint
    }
    val tagContent = if (isStatsExcluded) {
        contentSecondary
    } else {
        FriendlyMoneyColors.mintTintContent
    }
    val amountColor = if (isStatsExcluded) {
        contentSecondary
    } else if (info.isIncome) {
        MaterialTheme.moneyTalkColors.income
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardContainer
        ),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 아이콘: 지출=카테고리 아이콘 (원형 배경), 수입=이모지
                val category = info.category
                val iconEmoji = info.iconEmoji ?: info.categoryTag?.let { resolvedCategoryEmoji }
                if (category != null) {
                    CategoryIcon(
                        category = category,
                        containerSize = 40.dp,
                        fontSize = 22.dp
                    )
                } else if (iconEmoji != null) {
                    val bgColor = if (info.isIncome) {
                        MaterialTheme.moneyTalkColors.income.copy(alpha = 0.15f)
                    } else {
                        getCustomCategoryBackgroundColor(info.categoryTag.orEmpty())
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconEmoji, fontSize = 20.dp.toDpTextUnit)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    // 가게명 + 메모
                    val memoText = info.memoText
                    val titleText = buildAnnotatedString {
                        append(info.title)
                        if (!memoText.isNullOrBlank()) {
                            append(" ")
                            val memoStart = length
                            append("(")
                            append(memoText)
                            append(")")
                            addStyle(
                                style = SpanStyle(
                                    color = contentSecondary,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                ),
                                start = memoStart,
                                end = length
                            )
                        }
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = contentPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 카테고리 칩 + 시간 + 카드명 + 고정
                    val tag = info.categoryTag
                    val time = info.time
                    val cardName = info.cardNameText
                    if (tag != null || time != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 카테고리 태그 칩
                            if (tag != null) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tagContent,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(tagContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // 시간 + 카드명
                            val detail = buildAnnotatedString {
                                if (time != null) append(time)
                                if (cardName != null) {
                                    if (length > 0) append(" • ")
                                    append(cardName)
                                }
                            }
                            if (detail.text.isNotEmpty()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 고정 거래 태그
                            if (info.isFixed) {
                                Text(
                                    text = stringResource(R.string.transaction_card_fixed_tag),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FriendlyMoneyColors.Coral
                                )
                            }
                            if (isStatsExcluded) {
                                Text(
                                    text = stringResource(R.string.transaction_card_stats_excluded_tag),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentSecondary
                                )
                            }
                        }
                    } else {
                        // fallback: 기존 subtitle
                        Text(
                            text = info.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 금액 (-50,000원 / +10,000원)
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}
