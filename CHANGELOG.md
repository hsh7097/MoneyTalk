# Changelog

모든 주요 변경사항을 기록합니다.

## [Unreleased]

### Added
- 로컬 SMS 파싱 기능 (`SmsParser.kt`)
  - `parseSms()`: SMS 전체 파싱 (API 호출 없이 로컬 처리)
  - `extractStoreName()`: 가게명 추출
  - `extractDateTime()`: 날짜/시간 추출
  - `inferCategory()`: 카테고리 자동 추론
- 카테고리 키워드 매핑 (9개 카테고리)
  - 식비, 카페, 교통, 쇼핑, 구독, 의료/건강, 문화/여가, 교육, 생활
- Lottie 애니메이션 지원 추가
- README.md 문서 작성

### Changed
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
