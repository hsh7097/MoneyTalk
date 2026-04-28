package com.sanha.moneytalk.core.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.service.AppFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("UNUSED_PARAMETER")
class MoneyTalkChatAppFunctions(
    private val reader: MoneyTalkChatAppFunctionReader
) {
    /**
     * AI 채팅에서 지원하던 결정론적 조회/수정 작업을 AppFunction으로 사용할 수 있는지 확인합니다.
     *
     * @return 지원 여부와 노출된 작업 수.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun canExposeChatOperationAppFunctions(
        appFunctionContext: AppFunctionContext
    ): MoneyTalkOperationResult {
        return execute { reader.canExposeChatOperationAppFunctions() }
    }

    /**
     * 기간 내 총 지출을 조회합니다. 날짜를 생략하면 이번 달 1일부터 현재까지 조회합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param category 특정 카테고리만 조회할 때 사용하는 카테고리 표시명.
     * @return 총 지출 금액과 거래 수.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getTotalExpense(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null,
        category: String? = null
    ): MoneyTalkAmountSummary {
        return execute { reader.getTotalExpense(startDate, endDate, category) }
    }

    /**
     * 기간 내 총 수입을 조회합니다. 날짜를 생략하면 이번 달 1일부터 현재까지 조회합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @return 총 수입 금액과 거래 수.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getTotalIncome(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null
    ): MoneyTalkAmountSummary {
        return execute { reader.getTotalIncome(startDate, endDate) }
    }

    /**
     * 기간 내 카테고리별 지출 합계를 조회합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param category 특정 카테고리만 조회할 때 사용하는 카테고리 표시명.
     * @return 카테고리별 지출 합계.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getExpenseCategoryTotals(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null,
        category: String? = null
    ): MoneyTalkCategoryTotalsResponse {
        return execute { reader.getExpenseCategoryTotals(startDate, endDate, category) }
    }

    /**
     * 기간 내 지출 목록을 조회합니다. 응답은 최대 200건으로 제한됩니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param category 특정 카테고리만 조회할 때 사용하는 카테고리 표시명.
     * @param limit 반환할 최대 지출 건수.
     * @return 지출 거래 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getExpenses(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null,
        category: String? = null,
        limit: Int? = null
    ): MoneyTalkExpenseListResponse {
        return execute { reader.getExpenses(startDate, endDate, category, limit) }
    }

    /**
     * 특정 거래처 또는 거래처 별칭의 지출을 조회합니다.
     *
     * @param storeName 거래처명 또는 검색할 거래처 키워드.
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param limit 반환할 최대 지출 건수.
     * @return 거래처 지출 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getExpensesByStore(
        appFunctionContext: AppFunctionContext,
        storeName: String,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int? = null
    ): MoneyTalkExpenseListResponse {
        return execute { reader.getExpensesByStore(storeName, startDate, endDate, limit) }
    }

    /**
     * 특정 카드명으로 결제한 지출을 조회합니다.
     *
     * @param cardName 카드명 또는 카드명 일부.
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param limit 반환할 최대 지출 건수.
     * @return 카드별 지출 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getExpensesByCard(
        appFunctionContext: AppFunctionContext,
        cardName: String,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int? = null
    ): MoneyTalkExpenseListResponse {
        return execute { reader.getExpensesByCard(cardName, startDate, endDate, limit) }
    }

    /**
     * 기간 내 일별 지출 합계를 조회합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @return 일별 지출 합계.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDailyExpenseTotals(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null
    ): MoneyTalkDailyTotalsResponse {
        return execute { reader.getDailyExpenseTotals(startDate, endDate) }
    }

    /**
     * 저장된 전체 지출의 월별 합계를 조회합니다.
     *
     * @return 월별 지출 합계.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMonthlyExpenseTotals(
        appFunctionContext: AppFunctionContext
    ): MoneyTalkMonthlyTotalsResponse {
        return execute { reader.getMonthlyExpenseTotals() }
    }

    /**
     * 설정에 저장된 월 수입 값을 조회합니다.
     *
     * @return 설정된 월 수입.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getConfiguredMonthlyIncome(
        appFunctionContext: AppFunctionContext
    ): MoneyTalkAmountSummary {
        return execute { reader.getConfiguredMonthlyIncome() }
    }

    /**
     * 미분류 지출 항목을 조회합니다.
     *
     * @param limit 반환할 최대 지출 건수.
     * @return 미분류 지출 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getUncategorizedExpenses(
        appFunctionContext: AppFunctionContext,
        limit: Int? = null
    ): MoneyTalkExpenseListResponse {
        return execute { reader.getUncategorizedExpenses(limit) }
    }

    /**
     * 수입 대비 카테고리별 지출 비율을 조회합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param category 특정 카테고리만 조회할 때 사용하는 카테고리 표시명.
     * @return 카테고리별 지출 비율.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCategoryRatio(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null,
        category: String? = null
    ): MoneyTalkCategoryRatioResponse {
        return execute { reader.getCategoryRatio(startDate, endDate, category) }
    }

    /**
     * 거래처, 카테고리, 카드명, 메모에서 키워드가 포함된 지출을 검색합니다.
     *
     * @param keyword 검색 키워드.
     * @param limit 반환할 최대 지출 건수.
     * @return 검색된 지출 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchExpenses(
        appFunctionContext: AppFunctionContext,
        keyword: String,
        limit: Int? = null
    ): MoneyTalkExpenseListResponse {
        return execute { reader.searchExpenses(keyword, limit) }
    }

    /**
     * 지출 내역에 저장된 카드명 목록을 조회합니다.
     *
     * @return 카드명 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getUsedCards(
        appFunctionContext: AppFunctionContext
    ): MoneyTalkStringListResponse {
        return execute { reader.getUsedCards() }
    }

    /**
     * 수입 내역을 조회합니다. 날짜를 생략하면 전체 기간을 조회합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param limit 반환할 최대 수입 건수.
     * @return 수입 거래 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getIncomes(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null,
        limit: Int? = null
    ): MoneyTalkIncomeListResponse {
        return execute { reader.getIncomes(startDate, endDate, limit) }
    }

    /**
     * 중복으로 판단되는 지출 항목을 조회합니다.
     *
     * @param limit 반환할 최대 지출 건수.
     * @return 중복 지출 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDuplicateExpenses(
        appFunctionContext: AppFunctionContext,
        limit: Int? = null
    ): MoneyTalkExpenseListResponse {
        return execute { reader.getDuplicateExpenses(limit) }
    }

    /**
     * SMS 제외 키워드 목록을 조회합니다.
     *
     * @return SMS 제외 키워드 목록.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getSmsExclusionKeywords(
        appFunctionContext: AppFunctionContext
    ): MoneyTalkSmsExclusionKeywordsResponse {
        return execute { reader.getSmsExclusionKeywords() }
    }

    /**
     * 기간 내 예산 사용 현황을 조회합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @return 예산 한도, 사용액, 잔여액.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getBudgetStatus(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null
    ): MoneyTalkBudgetStatusResponse {
        return execute { reader.getBudgetStatus(startDate, endDate) }
    }

    /**
     * 지출을 필터링, 그룹핑, 집계하여 복합 분석합니다.
     *
     * @param startDate yyyy-MM-dd 형식 시작일.
     * @param endDate yyyy-MM-dd 형식 종료일.
     * @param filters AND 조건으로 적용할 필터 목록.
     * @param groupBy category, storeName, cardName, date, month, dayOfWeek, none 중 하나.
     * @param metrics sum, avg, count, max, min 메트릭 목록.
     * @param topN 반환할 상위 그룹 수.
     * @param sort asc 또는 desc.
     * @return 복합 지출 분석 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun analyzeExpenses(
        appFunctionContext: AppFunctionContext,
        startDate: String? = null,
        endDate: String? = null,
        filters: List<MoneyTalkAnalyticsFilter>? = null,
        groupBy: String? = null,
        metrics: List<MoneyTalkAnalyticsMetric>? = null,
        topN: Int? = null,
        sort: String? = null
    ): MoneyTalkAnalyticsResponse {
        return execute {
            reader.analyzeExpenses(startDate, endDate, filters, groupBy, metrics, topN, sort)
        }
    }

    /**
     * 특정 지출의 카테고리를 변경합니다.
     *
     * @param expenseId 지출 ID.
     * @param newCategory 새 카테고리 표시명.
     * @return 변경 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateExpenseCategory(
        appFunctionContext: AppFunctionContext,
        expenseId: Long? = null,
        newCategory: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.updateExpenseCategory(expenseId, newCategory) }
    }

    /**
     * 거래처명 기준으로 관련 지출 카테고리를 일괄 변경합니다.
     *
     * @param storeName 거래처명 또는 거래처 키워드.
     * @param newCategory 새 카테고리 표시명.
     * @return 변경 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateExpenseCategoryByStore(
        appFunctionContext: AppFunctionContext,
        storeName: String? = null,
        newCategory: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.updateExpenseCategoryByStore(storeName, newCategory) }
    }

    /**
     * 키워드가 포함된 거래처의 지출 카테고리를 일괄 변경합니다.
     *
     * @param keyword 거래처 검색 키워드.
     * @param newCategory 새 카테고리 표시명.
     * @return 변경 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateExpenseCategoryByKeyword(
        appFunctionContext: AppFunctionContext,
        keyword: String? = null,
        newCategory: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.updateExpenseCategoryByKeyword(keyword, newCategory) }
    }

    /**
     * 특정 지출을 삭제합니다.
     *
     * @param expenseId 삭제할 지출 ID.
     * @return 삭제 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteExpense(
        appFunctionContext: AppFunctionContext,
        expenseId: Long? = null
    ): MoneyTalkOperationResult {
        return execute { reader.deleteExpense(expenseId) }
    }

    /**
     * 키워드가 포함된 지출을 일괄 삭제합니다.
     *
     * @param keyword 거래처, 카테고리, 카드명, 메모 검색 키워드.
     * @return 삭제 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteExpensesByKeyword(
        appFunctionContext: AppFunctionContext,
        keyword: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.deleteExpensesByKeyword(keyword) }
    }

    /**
     * 중복 지출 항목을 삭제합니다.
     *
     * @return 삭제 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteDuplicateExpenses(
        appFunctionContext: AppFunctionContext
    ): MoneyTalkOperationResult {
        return execute { reader.deleteDuplicateExpenses() }
    }

    /**
     * 수동 지출을 추가합니다.
     *
     * @param storeName 거래처명.
     * @param amount 지출 금액.
     * @param date yyyy-MM-dd 형식 거래일. 생략하면 현재 시각을 사용합니다.
     * @param cardName 카드명 또는 결제수단.
     * @param category 카테고리 표시명.
     * @param memo 메모.
     * @return 추가 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addExpense(
        appFunctionContext: AppFunctionContext,
        storeName: String? = null,
        amount: Int? = null,
        date: String? = null,
        cardName: String? = null,
        category: String? = null,
        memo: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.addExpense(storeName, amount, date, cardName, category, memo) }
    }

    /**
     * 특정 지출의 메모를 수정합니다.
     *
     * @param expenseId 지출 ID.
     * @param memo 새 메모. null이면 메모를 비웁니다.
     * @return 수정 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateExpenseMemo(
        appFunctionContext: AppFunctionContext,
        expenseId: Long? = null,
        memo: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.updateExpenseMemo(expenseId, memo) }
    }

    /**
     * 특정 지출의 거래처명을 수정합니다.
     *
     * @param expenseId 지출 ID.
     * @param newStoreName 새 거래처명.
     * @return 수정 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateExpenseStoreName(
        appFunctionContext: AppFunctionContext,
        expenseId: Long? = null,
        newStoreName: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.updateExpenseStoreName(expenseId, newStoreName) }
    }

    /**
     * 특정 지출의 금액을 수정합니다.
     *
     * @param expenseId 지출 ID.
     * @param newAmount 새 지출 금액.
     * @return 수정 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun updateExpenseAmount(
        appFunctionContext: AppFunctionContext,
        expenseId: Long? = null,
        newAmount: Int? = null
    ): MoneyTalkOperationResult {
        return execute { reader.updateExpenseAmount(expenseId, newAmount) }
    }

    /**
     * SMS 제외 키워드를 추가합니다.
     *
     * @param keyword 제외할 키워드.
     * @return 추가 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addSmsExclusionKeyword(
        appFunctionContext: AppFunctionContext,
        keyword: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.addSmsExclusionKeyword(keyword) }
    }

    /**
     * SMS 제외 키워드를 삭제합니다. 기본 키워드는 삭제되지 않습니다.
     *
     * @param keyword 삭제할 키워드.
     * @return 삭제 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun removeSmsExclusionKeyword(
        appFunctionContext: AppFunctionContext,
        keyword: String? = null
    ): MoneyTalkOperationResult {
        return execute { reader.removeSmsExclusionKeyword(keyword) }
    }

    /**
     * 카테고리별 월 예산을 설정합니다.
     *
     * @param category 예산을 설정할 카테고리. 전체 예산은 전체를 사용합니다.
     * @param amount 월 예산 금액.
     * @return 설정 결과.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setBudget(
        appFunctionContext: AppFunctionContext,
        category: String? = null,
        amount: Int? = null
    ): MoneyTalkOperationResult {
        return execute { reader.setBudget(category, amount) }
    }

    private suspend fun <T> execute(block: suspend () -> T): T {
        return try {
            withContext(Dispatchers.IO) {
                block()
            }
        } catch (exception: IllegalArgumentException) {
            throw AppFunctionInvalidArgumentException(exception.message)
        }
    }
}
