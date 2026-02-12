package com.sanha.moneytalk.core.ui.component.transaction.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import java.text.NumberFormat
import java.util.Locale

/**
 * 그룹 헤더 Composable.
 * 모든 정렬 타입에서 동일한 레이아웃을 제공한다.
 * Info를 받아 순수 렌더링만 수행한다.
 *
 * 디자인: "수요일 10.23" + 우측 "-150,000원"
 */
@Composable
fun TransactionGroupHeaderCompose(
    info: TransactionGroupHeaderInfo,
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = info.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (info.incomeTotal > 0) {
                Text(
                    text = "+${
                        stringResource(
                            R.string.common_won,
                            numberFormat.format(info.incomeTotal)
                        )
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.moneyTalkColors.income
                )
            }
            if (info.expenseTotal > 0) {
                Text(
                    text = "-${
                        stringResource(
                            R.string.common_won,
                            numberFormat.format(info.expenseTotal)
                        )
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
