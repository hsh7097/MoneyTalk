package com.sanha.moneytalk.core.ui.component.transaction.card

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.model.Category

// ========== Preview용 테스트 데이터 ==========

private val expenseInfo = object : TransactionCardInfo {
    override val title = "스타벅스 강남점"
    override val subtitle = "식비 | 삼성카드"
    override val amount = 6500
    override val isIncome = false
    override val category = Category.FOOD
}

private val incomeInfo = object : TransactionCardInfo {
    override val title = "급여"
    override val subtitle = "급여 | 14:30"
    override val amount = 3500000
    override val isIncome = true
    override val iconEmoji = "\uD83D\uDCB0"
}

private val highAmountExpenseInfo = object : TransactionCardInfo {
    override val title = "월세"
    override val subtitle = "주거 | 국민은행"
    override val amount = 850000
    override val isIncome = false
    override val category = Category.HOUSING
}

private val longNameInfo = object : TransactionCardInfo {
    override val title = "서울특별시 강남구 테헤란로 123번길 맛있는 밥집 본점"
    override val subtitle = "식비 | 현대카드 (할부 3개월)"
    override val amount = 45000
    override val isIncome = false
    override val category = Category.FOOD
}

// ========== PreviewParameterProvider ==========

class TransactionCardPreviewProvider : PreviewParameterProvider<TransactionCardInfo> {
    override val values: Sequence<TransactionCardInfo>
        get() = sequenceOf(
            expenseInfo,
            incomeInfo,
            highAmountExpenseInfo,
            longNameInfo
        )
}

// ========== Preview ==========

@Preview(showBackground = true, name = "지출 카드")
@Composable
private fun ExpenseCardPreview() {
    MaterialTheme {
        TransactionCardCompose(
            info = expenseInfo,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "수입 카드")
@Composable
private fun IncomeCardPreview() {
    MaterialTheme {
        TransactionCardCompose(
            info = incomeInfo,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "긴 이름 카드")
@Composable
private fun LongNameCardPreview() {
    MaterialTheme {
        TransactionCardCompose(
            info = longNameInfo,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "카드 목록")
@Composable
private fun CardListPreview(
    @PreviewParameter(TransactionCardPreviewProvider::class)
    info: TransactionCardInfo
) {
    MaterialTheme {
        TransactionCardCompose(
            info = info,
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "지출+수입 혼합 목록")
@Composable
private fun MixedListPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                TransactionCardCompose(info = incomeInfo, onClick = {})
                HorizontalDivider()
                TransactionCardCompose(info = expenseInfo, onClick = {})
                HorizontalDivider()
                TransactionCardCompose(info = highAmountExpenseInfo, onClick = {})
            }
        }
    }
}
