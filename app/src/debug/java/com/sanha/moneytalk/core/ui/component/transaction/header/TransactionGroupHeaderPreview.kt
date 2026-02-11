package com.sanha.moneytalk.core.ui.component.transaction.header

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

// ========== Preview용 테스트 데이터 ==========

private val dateHeader = object : TransactionGroupHeaderInfo {
    override val title = "15일 (월)"
    override val expenseTotal = 45200
    override val incomeTotal = 3500000
}

private val expenseOnlyHeader = object : TransactionGroupHeaderInfo {
    override val title = "12일 (금)"
    override val expenseTotal = 23400
}

private val incomeOnlyHeader = object : TransactionGroupHeaderInfo {
    override val title = "25일 (화)"
    override val incomeTotal = 2500000
}

private val storeHeader = object : TransactionGroupHeaderInfo {
    override val title = "스타벅스 (5회)"
    override val expenseTotal = 27500
}

private val amountHeader = object : TransactionGroupHeaderInfo {
    override val title = "금액 높은순 (25건)"
    override val expenseTotal = 580000
}

// ========== PreviewParameterProvider ==========

class TransactionGroupHeaderPreviewProvider : PreviewParameterProvider<TransactionGroupHeaderInfo> {
    override val values: Sequence<TransactionGroupHeaderInfo>
        get() = sequenceOf(
            dateHeader,
            expenseOnlyHeader,
            incomeOnlyHeader,
            storeHeader,
            amountHeader
        )
}

// ========== Preview ==========

@Preview(showBackground = true, name = "날짜 헤더 (수입+지출)")
@Composable
private fun DateHeaderPreview() {
    MaterialTheme {
        TransactionGroupHeaderCompose(info = dateHeader)
    }
}

@Preview(showBackground = true, name = "지출만 있는 헤더")
@Composable
private fun ExpenseOnlyHeaderPreview() {
    MaterialTheme {
        TransactionGroupHeaderCompose(info = expenseOnlyHeader)
    }
}

@Preview(showBackground = true, name = "수입만 있는 헤더")
@Composable
private fun IncomeOnlyHeaderPreview() {
    MaterialTheme {
        TransactionGroupHeaderCompose(info = incomeOnlyHeader)
    }
}

@Preview(showBackground = true, name = "사용처별 헤더")
@Composable
private fun StoreHeaderPreview() {
    MaterialTheme {
        TransactionGroupHeaderCompose(info = storeHeader)
    }
}

@Preview(showBackground = true, name = "헤더 목록")
@Composable
private fun HeaderListPreview(
    @PreviewParameter(TransactionGroupHeaderPreviewProvider::class)
    info: TransactionGroupHeaderInfo
) {
    MaterialTheme {
        TransactionGroupHeaderCompose(info = info)
    }
}

@Preview(showBackground = true, name = "정렬별 헤더 모음")
@Composable
private fun AllHeadersPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                TransactionGroupHeaderCompose(info = dateHeader)
                TransactionGroupHeaderCompose(info = storeHeader)
                TransactionGroupHeaderCompose(info = amountHeader)
                TransactionGroupHeaderCompose(info = incomeOnlyHeader)
            }
        }
    }
}
