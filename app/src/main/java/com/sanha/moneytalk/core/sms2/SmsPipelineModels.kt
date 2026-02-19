package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.model.SmsAnalysisResult

/**
 * ===== SMS 통합 파이프라인 데이터 모델 =====
 *
 * 파이프라인 전체에서 사용하는 데이터 클래스.
 * SmsInput.body (원본 SMS)가 모든 단계에서 유지됨.
 *
 * 데이터 흐름:
 * SmsInput → (임베딩) → EmbeddedSms → (매칭/파싱) → SmsParseResult
 */

/**
 * 파이프라인 입력 단위
 *
 * 호출자가 SMS를 읽어서 이 형태로 변환하여 SmsPipeline.process()에 전달.
 * body가 원본이며 파이프라인 끝까지 보존됨.
 */
data class SmsInput(
    /** SMS 고유 ID (ContentResolver _id, 중복 체크용) */
    val id: String,
    /** ★ 원본 SMS 본문 (파싱/LLM 전달에 사용) */
    val body: String,
    /** 발신번호 (그룹핑 시 1차 분류 키) */
    val address: String,
    /** 수신 시간 (ms, 날짜/시간 추출에 사용) */
    val date: Long
)

/**
 * 임베딩 완료된 SMS
 *
 * SmsPipeline.batchEmbed()의 출력.
 * 원본(input)에 템플릿 + 벡터가 추가된 형태.
 *
 * - input.body: 원본 (파싱/LLM에 사용)
 * - template: 금액/날짜/가게명을 플레이스홀더로 치환한 텍스트 (임베딩 생성용)
 *   예: "[KB]{DATE} {TIME} {STORE} 체크카드출금 {AMOUNT} 잔액{AMOUNT}"
 * - embedding: template로부터 생성된 3072차원 벡터 (유사도 비교 + 그룹핑)
 */
data class EmbeddedSms(
    /** ★ 원본 포함 (input.body로 접근) */
    val input: SmsInput,
    /** 템플릿화된 텍스트 (SmsEmbeddingService.templateizeSms 결과) */
    val template: String,
    /** 3072차원 임베딩 벡터 (SmsEmbeddingService.generateEmbeddings 결과) */
    val embedding: List<Float>
)

/**
 * 파이프라인 최종 출력
 *
 * 결제로 확인되고 파싱이 성공한 SMS.
 * 호출자는 이것을 ExpenseEntity로 변환하여 DB에 저장.
 */
data class SmsParseResult(
    /** 원본 SMS */
    val input: SmsInput,
    /** 파싱 결과 (금액, 가게명, 카드명, 카테고리, 날짜) */
    val analysis: SmsAnalysisResult,
    /** 판정 tier: 2=벡터매칭(기존 패턴), 3=LLM(새 패턴 생성) */
    val tier: Int,
    /** 유사도 점수 (tier2) 또는 1.0 (tier3) */
    val confidence: Float
)
