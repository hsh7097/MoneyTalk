# Changelog

모든 주요 변경사항을 기록합니다.

## [Unreleased]

### Added (2026-02-09~11)
- **OwnedCard 시스템** (카드 화이트리스트)
  - OwnedCardEntity/Dao/Repository (DB v2→v3)
  - CardNameNormalizer: 25+ 카드사 명칭 정규화
  - SMS 동기화 시 자동 카드 등록 + Settings에서 소유 카드 관리
- **SMS 제외 키워드 시스템** (블랙리스트)
  - SmsExclusionKeywordEntity/Dao/Repository (DB v3→v4)
  - 기본(default) / 사용자(user) / 채팅(chat) 3가지 소스
- **DB 성능 인덱스** (v4→v5): expenses/incomes 테이블 인덱스
- **ANALYTICS 쿼리 타입**: 클라이언트 사이드 복합 분석
- **SMS 제외 채팅 액션**: add_sms_exclusion, remove_sms_exclusion
- **전역 스낵바 버스** (DataRefreshEvent 기반)
- **API 키 설정 후 저신뢰도 항목 자동 재분류**
- **UI 공통 컴포넌트**: TransactionCard, TransactionGroupHeader, SegmentedTabRow

### Refactored (2026-02-09~11)
- **프롬프트 XML 이전**: ChatPrompts.kt → string_prompt.xml (6종)
- **HistoryScreen Intent 패턴**: HistoryIntent sealed interface
- **FINANCIAL_ADVISOR 할루시네이션 개선**

### Added (2026-02-08)
- **계좌이체 카테고리 추가** (TRANSFER 🔄)
  - 계좌번호(**패턴) + "출금" 키워드 자동 감지
  - Gemini 분류 프롬프트 및 매핑 테이블 연동
- **보험 카테고리 복원** (INSURANCE 🛡️)
- **메모 기능** (지출/수입 모두 지원)
  - 상세 다이얼로그에서 메모 편집 (클릭 → 편집 다이얼로그)
  - 검색 시 메모 내용 포함
  - AI 채팅 조회 시 메모 표시
  - DB 마이그레이션 v1→v2 (incomes 테이블 memo 컬럼 추가)
- **홈 화면 지출 삭제 기능** (상세 다이얼로그에서 삭제 가능)
- **새 카테고리 4개 추가** (총 14개 카테고리)
  - 술/유흥 (DRINKING) 🍺: 술집, 바, 호프, 노래방
  - 운동 (FITNESS) 💪: 헬스장, 피트니스, 필라테스
  - 주거 (HOUSING) 🏢: 월세, 전세, 관리비
  - 경조 (EVENTS) 🎁: 축의금, 조의금, 선물
- **SMS 수입 파싱 기능**
  - `SmsParser.isIncomeSms()`: 입금 문자 판별
  - `SmsParser.extractIncomeAmount()`: 입금 금액 추출
  - `SmsParser.extractIncomeType()`: 입금 유형 추출 (급여, 이체, 환급 등)
  - `SmsParser.extractIncomeSource()`: 송금인/출처 추출
  - `SmsReader.readAllIncomeSms()`: 전체 수입 SMS 읽기
  - `SmsReader.readIncomeSmsByDateRange()`: 기간별 수입 SMS 읽기
- **IncomeEntity 필드 확장**
  - `smsId`: SMS 고유 ID (중복 방지)
  - `source`: 송금인/출처
  - `originalSms`: 원본 SMS 메시지
- 로컬 SMS 파싱 기능 (`SmsParser.kt`)
  - `parseSms()`: SMS 전체 파싱 (API 호출 없이 로컬 처리)
  - `extractStoreName()`: 가게명 추출
  - `extractDateTime()`: 날짜/시간 추출
  - `inferCategory()`: 카테고리 자동 추론
- Lottie 애니메이션 지원 추가
- README.md 문서 작성

### Refactored
- **VectorSearchEngine 책임 분리 (Phase 1 완료)**
  - Vector(연산) → Policy(판단) → Service(행동) 3계층 구조
  - core/similarity/ 패키지 신설: SimilarityPolicy, SmsPatternSimilarityPolicy, StoreNameSimilarityPolicy, CategoryPropagationPolicy
  - 모든 유사도 임계값을 SimilarityPolicy SSOT로 통합
- **Claude 레거시 코드 완전 제거**
  - ClaudeApi.kt, ClaudeRepository.kt, ClaudeModels.kt, PromptTemplates.kt 삭제
  - Retrofit 의존성 제거 (OkHttp만 유지)
  - NetworkModule에서 Retrofit/ClaudeApi DI 제거
  - HomeViewModel에서 ClaudeRepository 의존성 제거
- **SmsAnalysisResult를 core/model/로 분리** (ClaudeModels.kt에서 독립)
- **ExpenseRepository/ExpenseDao 미사용 메서드 5개 제거**
  - getExpensesByCardName, getExpensesByCardNameAndDateRange, getExpensesByCategoryAndDateRange, getExpensesByStoreNameAndDateRange, getTotalExpenseByStoreName
- **Tier 1.5b 그룹 기반 벡터 매칭 추가** (다수결, 유사도 ≥ 0.88)
- **CategoryReferenceProvider 신설** (동적 참조 리스트 → 모든 LLM 프롬프트 주입)
- **AI 채팅 액션 시스템 확장**
  - delete_by_keyword, add_expense, update_memo, update_store_name, update_amount 5개 액션 추가
  - FINANCIAL_ADVISOR 할루시네이션 방지 규칙 추가
- **SMS 동기화/카테고리 분류 다이얼로그 진행률 표시 개선**
  - 스피너 + 진행률 바 + "N/M건" 텍스트 실시간 업데이트
  - groupBySimilarity suspend 함수 변경 (yield로 UI 블로킹 방지)

### Changed
- **Gemini 카테고리 분류 개선**
  - 단일 카테고리만 반환하도록 프롬프트 강화
  - 괄호 안 추가 정보 파싱 시 자동 제거 (`보험 (의료/건강)` → `기타`)
  - 보험회사는 "기타"로 분류하도록 명시
  - 카테고리 매핑 테이블 확장 (술/유흥, 운동, 주거, 경조)
- **의미 없는 가게명 필터링 강화**
  - "국외발신", "해외발신" 등 발신 코드 제외
  - `KB]날짜시간` 형식 패턴 제외
  - 랜덤 문자열 (영문+숫자 5~8자) 제외
  - 보험/금융 코드 (`삼성화08003` 등) 제외
  - 카드번호 형식 (`롯데카드2508` 등) 제외
- SMS 파싱 방식 변경: Claude API → 로컬 정규식
  - `HomeViewModel.syncSmsMessages()` 수정
  - API 비용 절감, 오프라인 동작 가능

### Fixed
- **SMS 파싱 버그 3건 수정**
  - 결제예정 금액 SMS가 지출로 잡히는 문제 (excludeKeywords에 "결제금액" 추가)
  - 신한카드 승인 SMS에서 이름이 가게명으로 파싱되는 문제 (금액+일시불+시간 뒤 가게명 패턴 추가, 마스킹 이름 필터)
  - 계좌이체 출금 내역 카테고리 미분류 문제 (계좌이체 카테고리 자동 감지)
- Gradle 버전 호환성 문제 수정
  - AGP: 8.2.2
  - Kotlin: 1.9.22
  - KSP: 1.9.22-1.0.17
  - Gradle: 8.5
  - Compose Compiler: 1.5.10

---

## 버전 히스토리

### v1.0.0 (Initial)
- 초기 프로젝트 설정
- SMS 읽기 및 필터링 기능
- Room 데이터베이스 설정
- Claude API 연동 (AI 상담)
- Jetpack Compose UI 구현
