package com.sanha.moneytalk.core.ui.component.transaction.header

/**
 * 그룹 헤더 UI에 필요한 데이터를 정의하는 Interface.
 * 모든 정렬 타입(날짜별/사용처별/금액순)에서 공통으로 사용한다.
 */
interface TransactionGroupHeaderInfo {
    /** 그룹 제목 (예: "15일 (월)", "스타벅스 (5회)", "금액 높은순 (25건)") */
    val title: String

    /** 해당 그룹의 지출 총액 (원 단위). 0이면 미표시 */
    val expenseTotal: Int
        get() = 0

    /** 해당 그룹의 수입 총액 (원 단위). 0이면 미표시 */
    val incomeTotal: Int
        get() = 0
}
