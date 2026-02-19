package com.sanha.moneytalk.core.sms2

import android.util.Log
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
    private val pipeline: SmsPipeline
) {

    companion object {
        private const val TAG = "SmsSyncCoordinator"
    }

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
        onProgress: ((step: String, current: Int, total: Int) -> Unit)? = null
    ): SyncResult {
        if (smsList.isEmpty()) {
            return SyncResult(
                expenses = emptyList(),
                incomes = emptyList(),
                stats = SyncStats()
            )
        }

        Log.d(TAG, "=== process 시작: ${smsList.size}건 ===")

        // Step 0: 사전 필터링 (광고, 인증번호, 배송 알림 등 비결제 SMS 제거)
        // SmsPipeline 진입 전에 여기서 처리 → 불필요한 분류/임베딩 방지
        onProgress?.invoke("사전 필터링", 0, smsList.size)
        val filtered = preFilter.filter(smsList)
        Log.d(TAG, "사전 필터링: ${smsList.size}건 → ${filtered.size}건 (${smsList.size - filtered.size}건 제외)")

        if (filtered.isEmpty()) {
            return SyncResult(
                expenses = emptyList(),
                incomes = emptyList(),
                stats = SyncStats(totalInput = smsList.size, skipped = smsList.size)
            )
        }

        // Step 1: 수입/결제 분류 (필터 통과한 SMS만 대상)
        onProgress?.invoke("수입/결제 분류", 0, filtered.size)
        val (paymentCandidates, incomeCandidates, skipped) = incomeFilter.classifyAll(filtered)

        Log.d(TAG, "분류: 결제 ${paymentCandidates.size}건, 수입 ${incomeCandidates.size}건, 스킵 ${skipped.size}건")

        // Step 2: 결제 후보를 SmsPipeline에 전달 (사전 필터링 스킵)
        val expenses = if (paymentCandidates.isNotEmpty()) {
            pipeline.process(paymentCandidates, onProgress, skipPreFilter = true)
        } else {
            emptyList()
        }

        val preFilterSkipped = smsList.size - filtered.size
        val stats = SyncStats(
            totalInput = smsList.size,
            paymentCandidates = paymentCandidates.size,
            incomeCandidates = incomeCandidates.size,
            skipped = skipped.size + preFilterSkipped
        )

        Log.d(TAG, "=== 완료: 입력 ${smsList.size}건 → 지출 ${expenses.size}건 + 수입 ${incomeCandidates.size}건 ===")

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
