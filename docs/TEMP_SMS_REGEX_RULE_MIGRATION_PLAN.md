# [임시 문서] SMS Regex-First 전환 작업 계획

> **작업완료후 제거 예정 문서**
> 본 문서는 `sms2` 파이프라인을 `전화번호(sender) + regex 룰` 중심으로 전환하는 동안 컨텍스트 유실 방지를 위해 임시 운영합니다.

---

## 1. 목적

- SMS 파싱의 1차 경로를 임베딩 유사도 기반에서 **전화번호 기반 regex 매칭**으로 전환한다.
- 룰 소스를 2개로 운영한다.
- 앱 배포 기본 룰: 앱 내 JSON 파일
- 실시간 보정 룰: RTDB overlay
- 룰 중복 폭증을 막기 위해 **결정적 키(ruleKey)** 기반 upsert 정책을 적용한다.
- 미매칭만 기존 임베딩/LLM 경로를 타도록 하여 속도/비용/누락률을 동시에 개선한다.

## 2. 현재 기준선

- 기준 브랜치: `develop`
- 기준 커밋: `4e3de3f` (발신번호 저장 + debug 전체 동기화 머지 완료)
- 현재 메인 파이프라인:
- [`SmsSyncCoordinator.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsSyncCoordinator.kt)
- [`SmsPipeline.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPipeline.kt)
- [`SmsPatternMatcher.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPatternMatcher.kt)
- [`SmsGroupClassifier.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsGroupClassifier.kt)

## 2-1. 작업 히스토리

| 날짜 | Phase | PR/브랜치 | 변경 요약 | 상태 |
|------|-------|-----------|-----------|------|
| 2026-03-03 | Phase 1 | `codex/sms-regex-phase1-schema` | `sms_regex_rules` 엔티티/DAO/Repository 추가, DB v7 마이그레이션(6→7) 추가, AppDatabase/DatabaseModule 연결, `assembleDebug` 검증 완료 | 완료 |
| 2026-03-03 | Phase 2 | `codex/sms-regex-phase1-schema` | `assets/sms_rules_v1.json` 추가, `SmsRegexRuleAssetLoader` 추가(Asset JSON → `SmsRegexRuleEntity` 파싱) | 진행중 |

## 3. 확정 설계 원칙

- 파싱 실행 키는 `sender` 1차 조회를 사용한다.
- 룰 저장 키는 `(sender, type, ruleKey)`를 사용한다.
- `ruleKey`는 랜덤이 아니라 결정적 키를 사용한다.
- 같은 룰이면 어떤 기기에서 올라와도 같은 key로 upsert되어야 한다.
- 동일 룰 중복 저장 금지, 통계 필드만 누적한다.
- 실행 대상은 `ACTIVE` 룰만 사용한다.
- 파싱은 `priority DESC` 순차 매칭, 첫 성공 룰의 `type`을 최종 타입으로 사용한다.

## 4. 데이터 모델(목표)

### 4.1 RTDB 구조

```json
{
  "sms_rules": {
    "16449999": {
      "expense": {
        "a81f...": {
          "bodyRegex": "...",
          "amountGroup": "amount",
          "storeGroup": "store",
          "priority": 800,
          "status": "ACTIVE",
          "source": "asset|rtdb|llm",
          "matchCount": 0,
          "failCount": 0,
          "updatedAt": 1772523000000
        }
      }
    }
  }
}
```

### 4.2 로컬 DB 구조(목표 테이블)

- 테이블명 예시: `sms_regex_rules`
- 기본 PK: 복합키 `(senderAddress, type, ruleKey)`
- 주요 컬럼:
- `senderAddress`, `type`, `ruleKey`
- `bodyRegex`, `amountGroup`, `storeGroup`, `cardGroup`, `dateGroup`
- `priority`, `status`, `source`, `version`
- `matchCount`, `failCount`, `lastMatchedAt`, `updatedAt`, `createdAt`

### 4.3 ruleKey 생성

- 규칙:
- `ruleKey = sha256(sender|type|canonicalRegex|amountGroup|storeGroup|cardGroup|dateGroup|version)`
- `canonicalRegex`는 공백/개행 정규화 후 사용
- 목적:
- 기기별 랜덤 키 생성 방지
- 동일 룰 중복 저장 방지

## 5. 소스 우선순위/병합 정책

- 소스 1: 앱 내 JSON(기본 룰 seed)
- 소스 2: RTDB 룰(운영 overlay)
- 병합 규칙:
- 동일 `(sender,type,ruleKey)` 충돌 시 RTDB 값 우선
- 병합 완료 후 실행은 `priority DESC`
- 파싱 시에는 병합 결과(로컬 DB)만 사용

## 6. 단계별 작업 계획 (PR 분할)

## Phase 0. 기준선 정리

- 목표:
- 현재 문서/코드 상수 차이 정리, 추적 지표 baseline 확보
- 작업:
- `docs/SMS_PARSING.md`와 실제 상수값 일치 여부 점검
- baseline 로그 포맷 확정 (sender/type별 hit/fail/fallback)
- 완료 기준:
- baseline 측정 방법 문서화
- 회귀 비교 가능한 기준값 저장

## Phase 1. 룰 전용 로컬 스키마 추가

- 목표:
- `sms_patterns`와 분리된 regex 룰 저장소 생성
- 작업:
- `SmsRegexRuleEntity`, `SmsRegexRuleDao`, Repository 추가
- Room 버전 증가 + 마이그레이션 추가
- 인덱스:
- `(senderAddress, type, priority)`
- `(senderAddress, type, ruleKey)` 유니크
- 완료 기준:
- 마이그레이션 후 앱 실행/빌드 통과
- 기존 동기화 경로 영향 없음

## Phase 2. 룰 로더 구현 (Asset + RTDB)

- 목표:
- 앱 시작 시 기본룰 seed + RTDB overlay upsert
- 작업:
- `assets/sms_rules_v1.json` 포맷 정의
- `AssetRuleLoader`, `RtdbRuleLoader`, `RuleMergeService` 구현
- upsert 규칙 적용
- 완료 기준:
- 앱 재실행해도 룰 중복 생성 없음
- 동일 룰은 통계만 업데이트됨

## Phase 3. Fast Path 파서 추가

- 목표:
- 임베딩 경로 이전에 sender 기반 regex 매칭 수행
- 작업:
- `SmsRuleEngine`(신규) 추가
- 입력: `SmsInput`
- 출력: `SmsParseResult?` 또는 miss
- `SmsPipeline`에 Fast Path 삽입
- 완료 기준:
- 매칭 성공건은 Step3 임베딩 진입 없이 저장됨
- miss 건만 기존 Step3~5 진입

## Phase 4. Fallback 경로 안전 연결

- 목표:
- 누락 없이 기존 임베딩/LLM 경로 유지
- 작업:
- Fast Path miss를 기존 `SmsPatternMatcher/SmsGroupClassifier`로 전달
- 기존 통계와 신규 통계를 합산 노출
- 완료 기준:
- 파싱 누락/중복 저장 없음
- 기존 회귀 테스트 시나리오 통과

## Phase 5. 샘플 수집/룰 생성 파이프라인 정리

- 목표:
- 표본 중복 억제 + 룰 생성 입력 품질 확보
- 작업:
- `sms_samples` 저장 키를 결정적 키로 전환
- 원본문자/마스킹/템플릿/sender/type 저장 규칙 확정
- 룰 생성 스크립트 입력 포맷 고정
- 완료 기준:
- 같은 샘플 중복 업로드 크게 감소
- 룰 생성 입력이 sender/type 기준으로 정리됨

## Phase 6. 운영 최적화

- 목표:
- 룰 품질 자동 관리와 성능 튜닝
- 작업:
- `matchCount`, `failCount`, `lastMatchedAt` 기반 priority 보정
- 장기 미사용 룰 `INACTIVE` 전환 정책
- sender/type별 활성 룰 상한(예: 5개) 적용
- 완료 기준:
- fallback 비율 감소 추세
- 동기화 시간 개선 확인

## 7. 파일 영향 범위 (예상)

- 파이프라인:
- `core/sms2/SmsPipeline.kt`
- `core/sms2/SmsPatternMatcher.kt`
- `core/sms2/SmsSyncCoordinator.kt`
- 신규:
- `core/sms2/SmsRuleEngine.kt`
- `core/sms2/rules/*` (loader/merge/repository)
- DB:
- `core/database/entity/*`
- `core/database/dao/*`
- `core/database/AppDatabase.kt`
- `core/database/DatabaseMigrations.kt`
- 리소스/에셋:
- `app/src/main/assets/sms_rules_v1.json` (신규)
- 문서:
- `docs/SMS_PARSING.md` (최종 구조 반영)

## 8. 검증 계획

- 빌드:
- `./gradlew assembleDebug`
- 기능 검증:
- 동일 문자 재동기화 시 중복 저장 없는지 확인
- sender/type별 파싱 성공/실패 카운트 확인
- Fast Path hit 시 임베딩 호출 감소 확인
- 데이터 검증:
- 로컬 룰 키 중복 여부 점검
- RTDB pull 이후 upsert 동작 확인

## 9. 리스크와 대응

- 리스크:
- 룰 충돌로 오파싱 발생
- 대응:
- `status=ACTIVE` 룰만 실행 + 빠른 비활성화 필드 유지
- 리스크:
- 마이그레이션 실패
- 대응:
- 단계별 버전 업, 각 Phase별 빌드/실기기 검증
- 리스크:
- fallback 누락으로 데이터 손실
- 대응:
- Fast Path miss는 반드시 기존 Step3~5로 강제 전달

## 10. 롤백 전략

- 코드 롤백:
- Fast Path feature flag 비활성화 시 기존 임베딩 경로 100% 복귀
- 데이터 롤백:
- `sms_regex_rules` 테이블 비활성화 가능
- RTDB 룰 경로에서 `status=INACTIVE`로 즉시 차단 가능

## 11. 작업 추적 체크리스트

- [ ] Phase 0 완료
- [x] Phase 1 완료
- [ ] Phase 2 완료
- [ ] Phase 3 완료
- [ ] Phase 4 완료
- [ ] Phase 5 완료
- [ ] Phase 6 완료
- [ ] 문서 최종 정리 후 본 문서 삭제

## 12. 진행 로그 템플릿

| 날짜 | Phase | PR/커밋 | 변경 요약 | 남은 이슈 |
|------|-------|---------|-----------|-----------|
| YYYY-MM-DD | Pn | #PR / SHA | 내용 | 내용 |
