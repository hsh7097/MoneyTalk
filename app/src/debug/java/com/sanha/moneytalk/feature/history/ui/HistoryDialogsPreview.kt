package com.sanha.moneytalk.feature.history.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sanha.moneytalk.core.database.entity.IncomeEntity

// ========== 테스트 데이터 ==========

private val sampleIncome = IncomeEntity(
    id = 1,
    amount = 3500000,
    type = "급여",
    source = "주식회사 MoneyTalk",
    description = "2월 급여",
    isRecurring = true,
    recurringDay = 25,
    dateTime = System.currentTimeMillis(),
    originalSms = "[국민은행] 입금 3,500,000원 홍길동 02/25 잔액 5,230,000원",
    memo = "2월분 급여"
)

private val sampleIncomeNoMemo = IncomeEntity(
    id = 2,
    amount = 50000,
    type = "용돈",
    source = "부모님",
    description = "용돈",
    isRecurring = false,
    dateTime = System.currentTimeMillis(),
    originalSms = "[카카오뱅크] 입금 50,000원 김부모 02/10 잔액 1,280,000원"
)

// ========== AddExpenseDialog Preview ==========

@Preview(showBackground = true, name = "수동 지출 추가 다이얼로그")
@Composable
private fun AddExpenseDialogPreview() {
    MaterialTheme {
        AddExpenseDialog(
            onDismiss = {},
            onConfirm = { _, _, _, _ -> }
        )
    }
}

// ========== IncomeDetailDialog Preview ==========

@Preview(showBackground = true, name = "수입 상세 - 고정 수입 (메모 있음)")
@Composable
private fun IncomeDetailDialogPreview() {
    MaterialTheme {
        IncomeDetailDialog(
            income = sampleIncome,
            onDismiss = {},
            onDelete = {},
            onMemoChange = {}
        )
    }
}

@Preview(showBackground = true, name = "수입 상세 - 비고정 수입 (메모 없음)")
@Composable
private fun IncomeDetailDialogNoMemoPreview() {
    MaterialTheme {
        IncomeDetailDialog(
            income = sampleIncomeNoMemo,
            onDismiss = {},
            onDelete = {},
            onMemoChange = {}
        )
    }
}
