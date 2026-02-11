package com.sanha.moneytalk.core.ui.component.transaction.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import java.text.NumberFormat
import java.util.*

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
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val amountPrefix = if (info.isIncome) "+" else "-"
    val formattedAmount = "${amountPrefix}${stringResource(R.string.common_won, numberFormat.format(info.amount))}"

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                val iconEmoji = info.iconEmoji
                if (category != null) {
                    CategoryIcon(
                        category = category,
                        containerSize = 40.dp,
                        fontSize = 22.sp
                    )
                } else if (iconEmoji != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.moneyTalkColors.income.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconEmoji, fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    // 가게명
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 카테고리 칩 + 시간 + 카드명
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // 시간 + 카드명
                            val detail = buildString {
                                if (time != null) append(time)
                                if (cardName != null) {
                                    if (isNotEmpty()) append(" • ")
                                    append(cardName)
                                }
                            }
                            if (detail.isNotEmpty()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        // fallback: 기존 subtitle
                        Text(
                            text = info.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 금액 (-50,000원 / +10,000원)
            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (info.isIncome) MaterialTheme.moneyTalkColors.income else MaterialTheme.colorScheme.error
            )
        }
    }
}
