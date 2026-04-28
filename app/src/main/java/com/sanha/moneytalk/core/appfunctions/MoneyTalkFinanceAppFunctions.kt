package com.sanha.moneytalk.core.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MoneyTalkFinanceAppFunctions(
    private val summaryReader: MoneyTalkFinanceSummaryReader
) {
    /**
     * 척척 가계부에 저장된 거래 데이터를 기반으로 월간 가계 요약을 조회합니다.
     *
     * @param appFunctionContext App Functions 실행 컨텍스트.
     * @param year 조회할 연도. 생략하면 현재 앱 기준 월의 연도를 사용합니다.
     * @param month 조회할 월. 생략하면 현재 앱 기준 월을 사용합니다.
     * @return 수입, 지출, 예산, 전월 동기간 비교, 상위 카테고리, 최근 거래를 포함한 월간 요약.
     */
    @Suppress("UNUSED_PARAMETER")
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMonthlyFinanceSummary(
        appFunctionContext: AppFunctionContext,
        year: Int? = null,
        month: Int? = null
    ): MoneyTalkMonthlyFinanceSummary {
        return try {
            withContext(Dispatchers.IO) {
                summaryReader.readMonthlySummary(year, month)
            }
        } catch (exception: IllegalArgumentException) {
            throw AppFunctionInvalidArgumentException(exception.message)
        }
    }
}
