package com.sanha.moneytalk.core.ui.component.transaction.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.rememberCategoryEmoji
import com.sanha.moneytalk.core.util.toDpTextUnit
import java.text.NumberFormat
import java.util.Locale

/** 거래명과 금액을 먼저 보여주는 공통 행. 거래 변경은 상위 클릭/롱클릭에 위임한다. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionCardCompose(
    info: TransactionCardInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    val resolvedCategoryEmoji = rememberCategoryEmoji(info.categoryTag.orEmpty())
    val amountPrefix = if (info.isIncome) "+" else "-"
    val formattedAmount =
        "${amountPrefix}${stringResource(R.string.common_won, numberFormat.format(info.amount))}"
    val contentPrimary = if (info.isExcludedFromStats) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val contentSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val amountColor = if (info.isIncome) {
        MaterialTheme.moneyTalkColors.income
    } else {
        MaterialTheme.moneyTalkColors.expense
    }
    val titleText = buildAnnotatedString {
        append(info.title)
        info.memoText?.takeIf { it.isNotBlank() }?.let { memo ->
            append(" ")
            val start = length
            append("(")
            append(memo)
            append(")")
            addStyle(SpanStyle(color = contentSecondary), start, length)
        }
    }
    val metadata = listOfNotNull(info.categoryTag, info.time, info.cardNameText)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { info.subtitle }

    Card(
        modifier = modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.quick_transaction_title)
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 큰 글자/좁은 화면/큰 금액은 세로로 배치해 금액과 거래명이 경쟁하지 않게 한다.
            val stackAmount = LocalDensity.current.fontScale > 1.3f ||
                maxWidth < 320.dp || formattedAmount.length > 12
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val category = info.category
                val iconEmoji = info.iconEmoji ?: info.categoryTag?.let { resolvedCategoryEmoji }
                if (category != null) {
                    CategoryIcon(
                        category = category,
                        backgroundColorOverride = MaterialTheme.colorScheme.surfaceVariant,
                        containerSize = 40.dp,
                        fontSize = 21.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else if (iconEmoji != null) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = iconEmoji, fontSize = 20.dp.toDpTextUnit)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = contentPrimary,
                            maxLines = if (stackAmount) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!stackAmount) {
                            Text(
                                text = formattedAmount,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = amountColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    if (stackAmount) {
                        Text(
                            text = formattedAmount,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor
                        )
                    }
                    if (metadata.isNotBlank()) {
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (info.isFixed || info.isExcludedFromStats) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (info.isFixed) {
                                Text(
                                    text = stringResource(R.string.transaction_card_fixed_tag),
                                    modifier = Modifier.width(IntrinsicSize.Max),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentSecondary
                                )
                            }
                            if (info.isExcludedFromStats) {
                                Text(
                                    text = stringResource(R.string.transaction_card_stats_excluded_tag),
                                    modifier = Modifier.width(IntrinsicSize.Max),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
