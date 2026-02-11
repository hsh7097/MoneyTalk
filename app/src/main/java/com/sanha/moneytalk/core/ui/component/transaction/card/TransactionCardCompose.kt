package com.sanha.moneytalk.core.ui.component.transaction.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import java.text.NumberFormat
import java.util.*

private val IncomeColor = Color(0xFF4CAF50)

/**
 * 지출/수입 통합 거래 카드 Composable.
 * Info를 받아 순수 렌더링만 수행한다. 클릭은 onClick으로 상위에 위임.
 */
@Composable
fun TransactionCardCompose(
    info: TransactionCardInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val formattedAmount = stringResource(R.string.common_won, numberFormat.format(info.amount))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // 아이콘: 지출=카테고리 아이콘, 수입=이모지
            if (info.category != null) {
                CategoryIcon(
                    category = info.category!!,
                    containerSize = 32.dp,
                    fontSize = 20.sp
                )
            } else if (info.iconEmoji != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(IncomeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = info.iconEmoji!!, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = info.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 금액 (색상만으로 수입/지출 구분)
        Text(
            text = formattedAmount,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (info.isIncome) IncomeColor else MaterialTheme.colorScheme.error
        )
    }
}
