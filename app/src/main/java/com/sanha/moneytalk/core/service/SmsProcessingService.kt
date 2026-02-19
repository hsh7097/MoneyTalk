package com.sanha.moneytalk.core.service

import android.util.Log
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.HybridSmsClassifier
import com.sanha.moneytalk.core.util.SmsParser
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS 실시간 처리 서비스
 *
 * BroadcastReceiver에서 수신한 SMS를 파싱하여 DB에 저장합니다.
 * Hilt @Singleton으로 주입되어 SmsReceiver에서 사용됩니다.
 *
 * 처리 흐름:
 * 1. SmsParser.classifySmsType()으로 지출/수입 판별
 * 2. 지출: Regex 파싱 → 카테고리 분류(Tier 1~2) → DB 저장
 * 3. 수입: 금액/유형/출처 추출 → DB 저장
 * 4. DataRefreshEvent 발행 → UI 자동 갱신
 */
@Singleton
class SmsProcessingService @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val hybridSmsClassifier: HybridSmsClassifier,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val dataRefreshEvent: DataRefreshEvent
) {
    companion object {
        private const val TAG = "SmsProcessingService"
    }

    /**
     * 처리 결과
     */
    data class ProcessResult(
        val type: ResultType,
        val storeName: String = "",
        val amount: Int = 0
    ) {
        enum class ResultType {
            EXPENSE_SAVED,
            INCOME_SAVED,
            DUPLICATE,
            NOT_FINANCIAL,
            PARSE_FAILED
        }
    }

    /**
     * 수신된 SMS를 파싱하여 DB에 저장
     *
     * @param body SMS 본문
     * @param address 발신 번호
     * @param date 수신 시간 (밀리초)
     * @return 처리 결과
     */
    suspend fun processIncomingSms(body: String, address: String, date: Long): ProcessResult {
        try {
            // 사용자 제외 키워드 반영
            val userExcludeKeywords = smsExclusionRepository.getUserKeywords()
            SmsParser.setUserExcludeKeywords(userExcludeKeywords)

            val smsType = SmsParser.classifySmsType(body)

            // 지출 처리
            if (smsType.isPayment) {
                return processExpense(body, address, date)
            }

            // 수입 처리
            if (smsType.isIncome) {
                return processIncome(body, address, date)
            }

            return ProcessResult(ProcessResult.ResultType.NOT_FINANCIAL)
        } catch (e: Exception) {
            Log.e(TAG, "SMS 처리 실패: ${e.message}", e)
            return ProcessResult(ProcessResult.ResultType.PARSE_FAILED)
        }
    }

    private suspend fun processExpense(body: String, address: String, date: Long): ProcessResult {
        val smsId = SmsParser.generateSmsId(address, body, date)

        // 중복 체크
        if (expenseRepository.existsBySmsId(smsId)) {
            Log.d(TAG, "중복 SMS 무시: $smsId")
            return ProcessResult(ProcessResult.ResultType.DUPLICATE)
        }

        // Regex 파싱
        val result = hybridSmsClassifier.classifyRegexOnly(body, date)
        if (result == null || !result.isPayment || result.analysisResult == null) {
            Log.d(TAG, "Regex 파싱 실패: $smsId")
            return ProcessResult(ProcessResult.ResultType.PARSE_FAILED)
        }

        val analysis = result.analysisResult

        // 금액 유효성 검증
        if (analysis.amount <= 0) {
            Log.d(TAG, "잘못된 금액: ${analysis.amount}")
            return ProcessResult(ProcessResult.ResultType.PARSE_FAILED)
        }

        // 카테고리 분류 (Tier 1~2, Gemini 호출 안함)
        val category = if (analysis.category.isNotBlank() &&
            analysis.category != "미분류" &&
            analysis.category != "기타"
        ) {
            analysis.category
        } else {
            categoryClassifierService.getCategory(
                storeName = analysis.storeName,
                originalSms = body
            )
        }

        // ExpenseEntity 생성 및 저장
        val expense = ExpenseEntity(
            amount = analysis.amount,
            storeName = analysis.storeName,
            category = category,
            cardName = analysis.cardName,
            dateTime = DateUtils.parseDateTime(analysis.dateTime),
            originalSms = body,
            smsId = smsId
        )
        expenseRepository.insert(expense)
        Log.d(TAG, "지출 저장: ${analysis.storeName} ${analysis.amount}원 ($category)")

        // 카드 자동 등록
        if (analysis.cardName.isNotBlank()) {
            try {
                ownedCardRepository.registerCardsFromSync(listOf(analysis.cardName))
            } catch (e: Exception) {
                Log.w(TAG, "카드 자동 등록 실패 (무시): ${e.message}")
            }
        }

        // UI 갱신 이벤트
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)

        return ProcessResult(
            type = ProcessResult.ResultType.EXPENSE_SAVED,
            storeName = analysis.storeName,
            amount = analysis.amount
        )
    }

    private suspend fun processIncome(body: String, address: String, date: Long): ProcessResult {
        val smsId = SmsParser.generateSmsId(address, body, date)

        // 중복 체크
        if (incomeRepository.existsBySmsId(smsId)) {
            Log.d(TAG, "중복 수입 SMS 무시: $smsId")
            return ProcessResult(ProcessResult.ResultType.DUPLICATE)
        }

        val amount = SmsParser.extractIncomeAmount(body)
        if (amount <= 0) {
            return ProcessResult(ProcessResult.ResultType.PARSE_FAILED)
        }

        val incomeType = SmsParser.extractIncomeType(body)
        val source = SmsParser.extractIncomeSource(body)
        val dateTime = SmsParser.extractDateTime(body, date)

        val income = IncomeEntity(
            smsId = smsId,
            amount = amount,
            type = incomeType,
            source = source,
            description = if (source.isNotBlank()) "${source}에서 $incomeType" else incomeType,
            isRecurring = incomeType == "급여",
            dateTime = DateUtils.parseDateTime(dateTime),
            originalSms = body
        )
        incomeRepository.insert(income)
        Log.d(TAG, "수입 저장: ${amount}원 ($incomeType)")

        // UI 갱신 이벤트
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)

        return ProcessResult(
            type = ProcessResult.ResultType.INCOME_SAVED,
            amount = amount
        )
    }
}
