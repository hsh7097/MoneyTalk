package com.sanha.moneytalk.core.firebase

/**
 * Firebase Realtime Database에서 가져오는 프리미엄 설정
 *
 * DB 경로: /config
 * ```
 * {
 *   "free_tier_enabled": true,
 *   "service_enabled": true,
 *   "maintenance_message": "",
 *   "reward_ad_enabled": false,
 *   "credit_ad_enable": false,
 *   "reward_ad_chat_count": 2,
 *   "send_origin_message": true,
 *   "free_sync_count": 3,
 *   "min_version_code": 1,
 *   "min_version_name": "1.0.0",
 *   "force_update_message": "",
 *   "models": {
 *     "query_analyzer": "gemini-3.1-flash-lite",
 *     "financial_advisor": "gemini-3.1-flash-lite",
 *     "summary": "gemini-3.5-flash",
 *     "home_insight": "gemini-3.1-flash-lite",
 *     "category_classifier": "gemini-3.1-flash-lite",
 *     "sms_extractor": "gemini-3.1-flash-lite",
 *     "sms_regex_extractor": "gemini-3.5-flash",
 *     "sms_batch_extractor": "gemini-3.1-flash-lite"
 *   }
 * }
 * ```
 */
data class PremiumConfig(
    /** 무료 티어 허용 여부 — false면 무료 차단 */
    val freeTierEnabled: Boolean = true,
    /** 서비스 전체 활성화 여부 (점검 시 false) */
    val serviceEnabled: Boolean = true,
    /** 점검 시 표시할 메시지 */
    val maintenanceMessage: String = "",
    /** 리워드 광고 활성화 여부 (배너/월별 동기화 광고 공통 게이트) */
    val rewardAdEnabled: Boolean = false,
    /** AI 크레딧 광고/차감 기능 활성화 여부 */
    val creditAdEnabled: Boolean = false,
    /** 기존 RTDB 보상 수량 필드. 현재 AI 크레딧 광고 보상은 RewardAdManager에서 2로 고정한다. */
    val rewardAdChatCount: Int = 2,
    /** sms_origin 업로드 시 originBody 포함 여부 */
    val sendOriginMessage: Boolean = false,
    /** 광고 없이 허용되는 과거 월 동기화 무료 횟수 (기본 3회) */
    val freeSyncCount: Int = 3,
    /** 최소 요구 versionCode — 미만이면 강제 업데이트 */
    val minVersionCode: Int = 1,
    /** 최소 요구 versionName (표시용) */
    val minVersionName: String = "1.0.0",
    /** 강제 업데이트 시 표시할 커스텀 메시지 (빈 문자열이면 기본 메시지 사용) */
    val forceUpdateMessage: String = "",
    /** Gemini 모델명 원격 설정 */
    val modelConfig: GeminiModelConfig = GeminiModelConfig()
)

/**
 * Gemini 모델명 원격 관리 설정
 *
 * Firebase RTDB `/config/models/` 에서 읽어오며,
 * 값이 없으면 현재 기본값(companion object 상수)을 사용합니다.
 *
 * 용도: 코드 배포 없이 Firebase Console에서 모델 변경 가능
 */
data class GeminiModelConfig(
    /** 쿼리 분석 모델 (Step 1: 사용자 질문 → DB 쿼리 결정) */
    val queryAnalyzer: String = DEFAULT_QUERY_ANALYZER,
    /** 재무 상담 모델 (Step 3: 최종 답변 생성) */
    val financialAdvisor: String = DEFAULT_FINANCIAL_ADVISOR,
    /** 대화 요약 모델 (Rolling Summary, 타이틀 생성) */
    val summary: String = DEFAULT_SUMMARY,
    /** 홈 인사이트 모델 (한줄 AI 코멘트) */
    val homeInsight: String = DEFAULT_HOME_INSIGHT,
    /** 카테고리 분류 모델 (가게명 → 카테고리) */
    val categoryClassifier: String = DEFAULT_CATEGORY_CLASSIFIER,
    /** SMS 추출 모델 (단일 SMS → 결제 데이터) */
    val smsExtractor: String = DEFAULT_SMS_EXTRACTOR,
    /** SMS 정규식 생성 모델 (그룹 샘플 기반 regex 생성) */
    val smsRegexExtractor: String = DEFAULT_SMS_REGEX_EXTRACTOR,
    /** SMS 배치 추출 모델 (다건 SMS → 결제 데이터) */
    val smsBatchExtractor: String = DEFAULT_SMS_BATCH_EXTRACTOR
) {
    companion object {
        // 앱 배포 기본값은 운영 비용 방어를 위해 안정 Flash-Lite 계열을 유지하고,
        // 최신 preview 계열은 RTDB /config/models에서 내부 테스트로만 오버라이드한다.
        const val DEFAULT_QUERY_ANALYZER = "gemini-3.1-flash-lite"
        const val DEFAULT_FINANCIAL_ADVISOR = "gemini-3.1-flash-lite"
        const val DEFAULT_SUMMARY = "gemini-3.5-flash"
        const val DEFAULT_HOME_INSIGHT = "gemini-3.1-flash-lite"
        const val DEFAULT_CATEGORY_CLASSIFIER = "gemini-3.1-flash-lite"
        const val DEFAULT_SMS_EXTRACTOR = "gemini-3.1-flash-lite"
        const val DEFAULT_SMS_REGEX_EXTRACTOR = "gemini-3.5-flash"
        const val DEFAULT_SMS_BATCH_EXTRACTOR = "gemini-3.1-flash-lite"
    }
}
