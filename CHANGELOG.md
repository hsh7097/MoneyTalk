# Changelog

모든 주요 변경사항을 기록합니다.

## [Unreleased]

### Added
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
