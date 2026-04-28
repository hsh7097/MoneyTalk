package com.sanha.moneytalk.core.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/** 앱 기능 수정 작업 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkOperationResult(
    /** 작업 성공 여부. */
    val success: Boolean,
    /** 호출자가 분기할 수 있는 기계 판독용 결과 코드. */
    val resultCode: String,
    /** 변경된 항목 수. */
    val affectedCount: Int,
    /** 생성 또는 변경된 대표 항목 ID. 없으면 0. */
    val resourceId: Long
)

/** 지출 거래 요약. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkExpenseRecord(
    /** 앱 내부 지출 ID. */
    val id: Long,
    /** 거래처명. */
    val storeName: String,
    /** 지출 금액. */
    val amount: Int,
    /** 카테고리 표시명. */
    val category: String,
    /** 카드명 또는 결제수단. */
    val cardName: String,
    /** 거래 시각 timestamp. */
    val dateMillis: Long,
    /** yyyy-MM-dd HH:mm 형식의 거래 시각. */
    val dateText: String,
    /** 메모. 없으면 빈 문자열. */
    val memo: String
)

/** 수입 거래 요약. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkIncomeRecord(
    /** 앱 내부 수입 ID. */
    val id: Long,
    /** 수입 출처. */
    val source: String,
    /** 수입 설명. */
    val description: String,
    /** 수입 유형. */
    val type: String,
    /** 카테고리 표시명. */
    val category: String,
    /** 수입 금액. */
    val amount: Int,
    /** 수입 시각 timestamp. */
    val dateMillis: Long,
    /** yyyy-MM-dd HH:mm 형식의 수입 시각. */
    val dateText: String,
    /** 메모. 없으면 빈 문자열. */
    val memo: String
)

/** 금액 합계 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkAmountSummary(
    /** 조회 기간 시작 timestamp. */
    val periodStart: Long,
    /** 조회 기간 종료 timestamp. */
    val periodEnd: Long,
    /** 합계 금액. */
    val totalAmount: Int,
    /** 집계 대상 거래 수. */
    val transactionCount: Int
)

/** 지출 목록 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkExpenseListResponse(
    /** 조회 조건에 맞는 전체 건수. */
    val totalCount: Int,
    /** 응답에 포함된 건수. */
    val returnedCount: Int,
    /** 응답 항목의 금액 합계. */
    val returnedAmount: Int,
    /** 지출 거래 목록. */
    val expenses: List<MoneyTalkExpenseRecord>
)

/** 수입 목록 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkIncomeListResponse(
    /** 조회 조건에 맞는 전체 건수. */
    val totalCount: Int,
    /** 응답에 포함된 건수. */
    val returnedCount: Int,
    /** 응답 항목의 금액 합계. */
    val returnedAmount: Int,
    /** 수입 거래 목록. */
    val incomes: List<MoneyTalkIncomeRecord>
)

/** 카테고리별 지출 합계. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkCategoryTotal(
    /** 카테고리 표시명. */
    val category: String,
    /** 해당 카테고리 지출 합계. */
    val amount: Int,
    /** 전체 지출 대비 비율. 100은 1%를 의미합니다. */
    val expenseRatioPercentX100: Int
)

/** 카테고리별 지출 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkCategoryTotalsResponse(
    /** 조회 기간 시작 timestamp. */
    val periodStart: Long,
    /** 조회 기간 종료 timestamp. */
    val periodEnd: Long,
    /** 전체 지출 합계. */
    val totalAmount: Int,
    /** 카테고리별 지출 합계 목록. */
    val categories: List<MoneyTalkCategoryTotal>
)

/** 일별 지출 합계. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkDailyTotal(
    /** yyyy-MM-dd 형식 날짜. */
    val date: String,
    /** 해당 날짜 지출 합계. */
    val amount: Int
)

/** 일별 지출 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkDailyTotalsResponse(
    /** 일별 지출 합계 목록. */
    val days: List<MoneyTalkDailyTotal>
)

/** 월별 지출 합계. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkMonthlyTotal(
    /** yyyy-MM 형식 월. */
    val month: String,
    /** 해당 월 지출 합계. */
    val amount: Int
)

/** 월별 지출 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkMonthlyTotalsResponse(
    /** 월별 지출 합계 목록. */
    val months: List<MoneyTalkMonthlyTotal>
)

/** 문자열 목록 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkStringListResponse(
    /** 전체 항목 수. */
    val totalCount: Int,
    /** 문자열 항목 목록. */
    val items: List<String>
)

/** 카테고리별 비율 분석 항목. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkCategoryRatioItem(
    /** 카테고리 표시명. */
    val category: String,
    /** 카테고리 지출 합계. */
    val amount: Int,
    /** 월 수입 대비 비율. 100은 1%를 의미합니다. */
    val incomeRatioPercentX100: Int,
    /** 전체 지출 대비 비율. 100은 1%를 의미합니다. */
    val expenseRatioPercentX100: Int
)

/** 카테고리별 비율 분석 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkCategoryRatioResponse(
    /** 설정된 월 수입. */
    val monthlyIncome: Int,
    /** 전체 지출 합계. */
    val totalExpense: Int,
    /** 전체 지출의 월 수입 대비 비율. 100은 1%를 의미합니다. */
    val totalIncomeRatioPercentX100: Int,
    /** 카테고리별 비율 목록. */
    val categories: List<MoneyTalkCategoryRatioItem>
)

/** SMS 제외 키워드 항목. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkSmsExclusionKeyword(
    /** 제외 키워드. */
    val keyword: String,
    /** 키워드 출처. default, user, chat 중 하나. */
    val source: String
)

/** SMS 제외 키워드 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkSmsExclusionKeywordsResponse(
    /** 전체 키워드 수. */
    val totalCount: Int,
    /** 제외 키워드 목록. */
    val keywords: List<MoneyTalkSmsExclusionKeyword>
)

/** 예산 상태 항목. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkBudgetStatusItem(
    /** 카테고리 표시명. */
    val category: String,
    /** 설정된 월 예산. */
    val budget: Int,
    /** 조회 기간 내 사용 금액. */
    val spent: Int,
    /** 남은 금액. 초과 시 음수. */
    val remaining: Int
)

/** 예산 상태 조회 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkBudgetStatusResponse(
    /** 예산 설정 여부. */
    val budgetConfigured: Boolean,
    /** 카테고리별 예산 상태. */
    val budgets: List<MoneyTalkBudgetStatusItem>
)

/** 복합 지출 분석 필터. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkAnalyticsFilter(
    /** 필터 필드. category, storeName, cardName, amount, memo, dayOfWeek 중 하나. */
    val field: String,
    /** 비교 연산자. ==, !=, >, >=, <, <=, contains, not_contains, in, not_in 중 하나. */
    val op: String,
    /** 비교 값. in/not_in은 쉼표로 구분합니다. */
    val value: String,
    /** 카테고리 필터에서 하위 카테고리를 포함할지 여부. */
    val includeSubcategories: Boolean
)

/** 복합 지출 분석 메트릭. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkAnalyticsMetric(
    /** 집계 연산. sum, avg, count, max, min 중 하나. */
    val op: String,
    /** 집계 필드. 현재 amount를 사용합니다. */
    val field: String
)

/** 복합 지출 분석 메트릭 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkAnalyticsMetricResult(
    /** 집계 연산. */
    val op: String,
    /** 집계 필드. */
    val field: String,
    /** 집계 값. */
    val value: Int,
    /** 값 단위. won 또는 count. */
    val unit: String
)

/** 복합 지출 분석 그룹 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkAnalyticsGroupResult(
    /** 그룹 키. groupBy가 없으면 all. */
    val groupKey: String,
    /** 그룹별 메트릭 결과. */
    val metrics: List<MoneyTalkAnalyticsMetricResult>
)

/** 복합 지출 분석 결과. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkAnalyticsResponse(
    /** 필터 적용 후 지출 건수. */
    val filteredCount: Int,
    /** 그룹핑 기준. 없으면 none. */
    val groupBy: String,
    /** 정렬 방향. asc 또는 desc. */
    val sort: String,
    /** 분석 결과 목록. */
    val results: List<MoneyTalkAnalyticsGroupResult>
)
