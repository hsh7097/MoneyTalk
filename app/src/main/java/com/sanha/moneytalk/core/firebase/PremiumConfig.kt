package com.sanha.moneytalk.core.firebase

/**
 * Firebase Realtime Database에서 가져오는 프리미엄 설정
 *
 * DB 경로: /config
 * ```
 * {
 *   "gemini_api_key": "서버 관리 API 키 (단일, 하위호환)",
 *   "gemini_api_keys": ["key1", "key2", ...],
 *   "free_tier_enabled": true,
 *   "service_enabled": true,
 *   "maintenance_message": "",
 *   "reward_ad_enabled": false,
 *   "reward_ad_chat_count": 5,
 *   "min_version_code": 1,
 *   "min_version_name": "1.0.0",
 *   "force_update_message": "",
 *   "models": {
 *     "query_analyzer": "gemini-2.5-pro",
 *     "financial_advisor": "gemini-2.5-pro",
 *     "summary": "gemini-2.5-flash",
 *     "home_insight": "gemini-2.5-flash-lite",
 *     "category_classifier": "gemini-2.5-flash-lite",
 *     "sms_extractor": "gemini-2.5-flash-lite",
 *     "sms_batch_extractor": "gemini-2.5-flash-lite",
 *     "embedding": "gemini-embedding-001"
 *   }
 * }
 * ```
 */
data class PremiumConfig(
    /** 서버에서 관리하는 Gemini API 키 (단일 키, 하위호환용) */
    val geminiApiKey: String = "",
    /** API 키 풀 (라운드로빈 분산용). 비어있으면 geminiApiKey 단일 키 사용 */
    val geminiApiKeys: List<String> = emptyList(),
    /** 무료 티어 허용 여부 — false면 무료 차단 */
    val freeTierEnabled: Boolean = true,
    /** 서비스 전체 활성화 여부 (점검 시 false) */
    val serviceEnabled: Boolean = true,
    /** 점검 시 표시할 메시지 */
    val maintenanceMessage: String = "",
    /** 리워드 광고 활성화 여부 (true면 채팅 시 광고 시청 필요) */
    val rewardAdEnabled: Boolean = false,
    /** 리워드 광고 1회 시청 시 충전되는 채팅 횟수 */
    val rewardAdChatCount: Int = 5,
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
    /** SMS 배치 추출 모델 (다건 SMS → 결제 데이터) */
    val smsBatchExtractor: String = DEFAULT_SMS_BATCH_EXTRACTOR,
    /** 임베딩 모델 (SMS/가게명 벡터 생성, REST API) */
    val embedding: String = DEFAULT_EMBEDDING
) {
    companion object {
        const val DEFAULT_QUERY_ANALYZER = "gemini-2.5-pro"
        const val DEFAULT_FINANCIAL_ADVISOR = "gemini-2.5-pro"
        const val DEFAULT_SUMMARY = "gemini-2.5-flash"
        const val DEFAULT_HOME_INSIGHT = "gemini-2.5-flash-lite"
        const val DEFAULT_CATEGORY_CLASSIFIER = "gemini-2.5-flash-lite"
        const val DEFAULT_SMS_EXTRACTOR = "gemini-2.5-flash-lite"
        const val DEFAULT_SMS_BATCH_EXTRACTOR = "gemini-2.5-flash-lite"
        const val DEFAULT_EMBEDDING = "gemini-embedding-001"
    }
}
