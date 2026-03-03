package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== SMS 동기화 코디네이터 (sms2 패키지의 유일한 외부 진입점) =====
 *
 * 역할: sms2 패키지의 단일 진입점.
 *       외부에서 SMS를 처리할 때 반드시 이 클래스의 process()를 통해 진입한다.
 *       SmsPipeline, SmsPreFilter 등 하위 컴포넌트를 직접 호출하지 않는다.
 *
 * 호출자(HomeViewModel)가 SMS를 읽어서 List<SmsInput>으로 전달하면,
 * 수입/결제를 분류하고 결제 SMS만 SmsPipeline에 전달.
 *
 * 데이터 흐름:
 * 호출자: SMS 읽기 (ContentResolver) → List<SmsInput> 변환 → 중복 제거
 *   → SmsSyncCoordinator.process(smsList)
 *   → SmsPreFilter.filter() → 비결제 SMS 사전 제거 (광고, 인증번호 등)
 *   → SmsIncomeFilter.classifyAll() → 수입 vs 결제 분리
 *   → SmsPipeline.process(결제 후보, skipPreFilter=true) → List<SmsParseResult>
 *   → SyncResult(expenses, incomes) 반환
 *   → 호출자: ExpenseEntity/IncomeEntity 변환 + DB 저장
 *
 * 이 클래스가 하지 않는 것:
 * - SMS 읽기 (ContentResolver 접근 없음, 호출자 책임)
 * - lastSyncTime 관리 (호출자 책임)
 * - DB 저장 (호출자 책임)
 * - 카드 자동 등록 (호출자 책임)
 * - 카테고리 분류 (호출자 책임)
 *
 * 의존성: SmsPreFilter (sms2), SmsIncomeFilter (sms2), SmsPipeline (sms2)
 * ※ core/sms 패키지 미참조
 */
@Singleton
class SmsSyncCoordinator @Inject constructor(
    private val preFilter: SmsPreFilter,
    private val incomeFilter: SmsIncomeFilter,
    private val pipeline: SmsPipeline,
    private val regexRuleMatcher: SmsRegexRuleMatcher,
    private val regexRuleSyncService: SmsRegexRuleSyncService
) {

    /**
     * ★ sms2 패키지의 유일한 외부 진입점 ★
     *
     * 모든 SMS 처리(배치 동기화, 증분 동기화, 실시간 수신)는
     * 반드시 이 메소드를 통해 진입해야 한다.
     * SmsPipeline, SmsPreFilter 등 하위 컴포넌트를 직접 호출하지 않는다.
     *
     * 호출 경로:
     *   HomeViewModel.syncSmsV2() → SmsSyncCoordinator.process()
     *
     * 내부 처리 순서:
     *   Step 0: SmsPreFilter — 비결제 SMS 사전 제거
     *   Step 1: SmsIncomeFilter — 결제/수입/스킵 분류
     *   Step 2: SmsPipeline — 임베딩 → 벡터매칭 → LLM
     *
     * 호출자가 SMS를 읽어서 List<SmsInput>으로 전달.
     * 기간/증분 구분은 호출자가 SMS 읽는 시점에서 결정 → 여기서는 구분 불필요.
     *
     * 사용 예:
     *   // 배치 동기화 (10월 전체)
     *   val smsList = smsReader.readByDateRange(oct1, oct31).map { it.toSmsInput() }
     *   val result = coordinator.process(smsList)
     *
     *   // 증분 동기화 (앱 재시작 / 실시간)
     *   val smsList = smsReader.readByDateRange(lastSyncTime, now).map { it.toSmsInput() }
     *   val result = coordinator.process(smsList)
     *
     * @param smsList 처리할 SMS 목록 (호출자가 읽기 + 중복 제거 완료)
     * @param onProgress 진행률 콜백 (UI 표시용)
     * @return SyncResult (지출 결과 + 수입 원본 + 통계)
     */
    suspend fun process(
        smsList: List<SmsInput>,
        onProgress: ((stepIndex: Int, step: String, current: Int, total: Int) -> Unit)? = null
    ): SyncResult {
        if (smsList.isEmpty()) {
            return SyncResult(
                expenses = emptyList(),
                incomes = emptyList(),
                stats = SyncStats()
            )
        }
        MoneyTalkLogger.i("SmsSyncCoordinator 시작: 입력 ${smsList.size}건")

        runCatching {
            regexRuleSyncService.syncRules()
        }.onFailure { e ->
            MoneyTalkLogger.w("Regex 룰 동기화 실패 (폴백 진행): ${e.message}")
        }

        // Step 0: 사전 필터링 (광고, 인증번호, 배송 알림 등 비결제 SMS 제거)
        // SmsPipeline 진입 전에 여기서 처리 → 불필요한 분류/임베딩 방지
        onProgress?.invoke(SmsPipeline.STEP_FILTER, "문자 분류 준비 중...", 0, smsList.size)
        val filtered = preFilter.filter(smsList)
        MoneyTalkLogger.i("Step0 PreFilter: ${smsList.size}건 → ${filtered.size}건")

        if (filtered.isEmpty()) {
            return SyncResult(
                expenses = emptyList(),
                incomes = emptyList(),
                stats = SyncStats(totalInput = smsList.size, skipped = smsList.size)
            )
        }

        // Step 1: 수입/결제 분류 (필터 통과한 SMS만 대상)
        onProgress?.invoke(SmsPipeline.STEP_FILTER, "결제 문자 찾는 중...", 0, filtered.size)
        val (paymentCandidates, incomeCandidates, skipped) = incomeFilter.classifyAll(filtered)
        MoneyTalkLogger.i("Step1 IncomeFilter: 결제 ${paymentCandidates.size}건, 수입 ${incomeCandidates.size}건, 스킵 ${skipped.size}건"
        )

        // Step 1.5: sender 기반 로컬 regex 룰 매칭 (Fast Path)
        val regexMatchResult = regexRuleMatcher.matchPaymentCandidates(paymentCandidates)
        val fastPathMatched = regexMatchResult.matched
        val fastPathUnmatched = regexMatchResult.unmatched
        MoneyTalkLogger.i("Step1.5 SenderRegex: 매칭 ${fastPathMatched.size}건, 폴백 ${fastPathUnmatched.size}건")

        // Step 2: Fast Path 미매칭 결제 후보만 SmsPipeline에 전달 (사전 필터링 스킵)
        val pipelineResult = if (fastPathUnmatched.isNotEmpty()) {
            pipeline.process(fastPathUnmatched, onProgress, skipPreFilter = true)
        } else {
            SmsPipeline.PipelineResult(emptyList(), 0, 0)
        }

        val expenses = (fastPathMatched + pipelineResult.results)
            .distinctBy { it.input.id }
        val duplicateExpenseCount = fastPathMatched.size + pipelineResult.results.size - expenses.size
        if (duplicateExpenseCount > 0) {
            MoneyTalkLogger.w("중복 파싱 결과 감지: ${duplicateExpenseCount}건 (동일 SMS id 기준)")
        }

        val unresolvedPayments = (paymentCandidates.size - expenses.size - pipelineResult.droppedCount)
            .coerceAtLeast(0)
        if (unresolvedPayments > 0) {
            MoneyTalkLogger.w(
                "결제 후보 대비 미해결 건 감지: ${unresolvedPayments}건 " +
                    "(candidates=${paymentCandidates.size}, parsed=${expenses.size}, dropped=${pipelineResult.droppedCount})"
            )
        }

        val preFilterSkipped = smsList.size - filtered.size
        val totalVectorMatchCount = fastPathMatched.size + pipelineResult.vectorMatchCount
        val stats = SyncStats(
            totalInput = smsList.size,
            paymentCandidates = paymentCandidates.size,
            incomeCandidates = incomeCandidates.size,
            skipped = skipped.size + preFilterSkipped,
            fastPathMatchCount = fastPathMatched.size,
            fallbackToPipelineCount = fastPathUnmatched.size,
            pipelineVectorMatchCount = pipelineResult.vectorMatchCount,
            vectorMatchCount = totalVectorMatchCount,
            llmProcessCount = pipelineResult.llmProcessCount,
            newPatternsCreated = pipelineResult.llmProcessCount,
            regexFailedRecoveredCount = pipelineResult.regexFailedRecoveredCount,
            pipelineDroppedCount = pipelineResult.droppedCount
        )
        MoneyTalkLogger.i("SmsSyncCoordinator 완료: 지출 ${expenses.size}건, 수입 ${incomeCandidates.size}건"
        )


        return SyncResult(
            expenses = expenses,
            incomes = incomeCandidates,
            stats = stats
        )
    }

    /**
     * 사용자 제외 키워드 설정
     *
     * 호출자가 동기화 시작 전에 DB에서 로드하여 전달.
     * SmsIncomeFilter에 위임.
     *
     * @param keywords lowercase 변환된 키워드 Set
     */
    fun setUserExcludeKeywords(keywords: Set<String>) {
        incomeFilter.setUserExcludeKeywords(keywords)
    }
}
