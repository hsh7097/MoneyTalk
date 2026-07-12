---
type: reference
title: Release 1.0.3 Development Summary
description: 1.0.2 태그 이후 1.0.3 배포 후보까지의 기능, 안정화, 검증, 운영 결정을 커밋과 KB 기준으로 통합한다.
tags: [moneytalk, release, 1.0.3, changelog, verification]
resource: 1.0.2..fa07dda
timestamp: 2026-07-13T01:11:00+09:00
status: verified
---

# 1.0.3 개발 요약

> 기준 태그: `1.0.2` / `0a604e39e1ef648a56f992985ff9f363407c327b` / `versionCode 18`
> 배포 후보: `fa07ddac2f90644016acf5cb1b2f29af3408db73` / `versionName 1.0.3` / `versionCode 19`
> 범위: `1.0.2..fa07dda`, 총 28개 커밋

이 문서는 1.0.2 이후 변경을 버전 단위로 복원하는 진입점이다.
구현 세부는 연결된 기능 KB와 현재 코드를 우선하고, 검증 수치와 배포 판정은 릴리즈 worklog를 기준으로 한다.
전체 diff는 382개 파일이지만 기존 루트 문서를 `moneytalk-kb`로 흡수한 이동량이 크므로 제품 변경 규모를 단순 파일 수로 판단하지 않는다.

## 최종 사용자 영향 요약

| 영역 | 1.0.3 변경 | 사용자 영향 |
|---|---|---|
| AI 크레딧 | 잔액·원장·설정 화면, 보상형 광고 충전, 차감·환불·이관 정책 추가 | 기능 gate가 열리면 채팅과 과거 월 동기화를 크레딧으로 사용 |
| AI 채팅 | 단순 조회 local fast path, 앱 내부 금액 계산, 답변 숫자 guard, 오류·재시도 보강 | 단순 질문 응답 속도와 수치 신뢰도 개선, 불필요한 Gemini 호출 감소 |
| AI 인프라 | Firebase AI Logic 전환, Play Integrity App Check, 호출 제한과 실패 circuit | client API key 제거, 인증 실패 반복 호출 억제 |
| 문자 파싱 | sender regex 룰 확대, 안내문·수입·중복·제외 조건 보강 | 거래 인식률 향상과 오탐·중복 저장 감소 |
| 알림 수신 | 금융 앱 알림 분류와 SMS 교차 중복 보정 | RCS·앱 알림 거래의 중복 노티와 중복 저장 감소 |
| 카테고리 | `배달` leaf 추가, StoreRule/vector/keyword/Gemini 단계 보강 | 세부 카테고리 분류와 local fallback 안정화 |
| 임베딩 | 네트워크 독립적인 결정적 local 768차원 vector로 전환 | API key 없이 유사도 검색 가능, 기존 vector 자동 재생성 |
| 동기화 | sync·분류·삭제 상호배제, epoch 무효화, 실제 차감분 환불 | 재진입·삭제·광고가 겹칠 때 데이터와 크레딧 일관성 개선 |
| UI | 최대 글자, 차트 라벨, picker, 내보내기, 날짜·시간 배치 보완 | 큰 글자와 좁은 화면에서 잘림·겹침 감소 |
| KB | 화면·기능·서브모듈 KB와 agent routing 구축 | 이후 AI 작업이 진입점, 계약, 변경 이력을 코드와 함께 복원 가능 |

## AI 크레딧과 광고

최종 정책은 다음과 같다.

- 빈 문자열이 아닌 채팅 1회는 1크레딧이다.
- 과거 월 문자 동기화는 실행 월당 1크레딧이다.
- 보상형 광고 1회 보상 기본값은 2크레딧이며 RTDB 설정값으로 조정 가능하다.
- 크레딧 기능은 monetization, `credit_ad_enable`, FREE tier 조건을 모두 만족할 때만 노출한다.
- 기존 리워드 잔여분 이관은 Mutex로 1회만 수행한다.
- 답변 실패, 동기화 취소·실패, 등록 gate 무효화 시 실제 차감된 금액만 환불한다.
- 광고가 보상 없이 닫히거나 표시 자체가 실패하면 크레딧과 월별 동기화를 진행하지 않는다.
- SMS 권한을 광고보다 먼저 확인하며, 권한 거부 시 광고를 소비하지 않고 요청 dialog를 유지한다.
- 광고 표시를 위해 dialog UI를 숨겨도 pending sync epoch는 유지하고, 보상 성공 callback이 같은 요청을 이어서 처리한다.

세부 계약은 [AI 크레딧·수익화 정책](../budget-credit-monetization/02-policy-and-plans.md)을 본다.

## AI 채팅과 Gemini

- 안전한 단순 조회는 `LocalChatQueryRouter`가 Gemini analyzer/final/summary 밖에서 처리한다.
- 총지출, 카테고리 지출, 최근 거래, 예산 현황처럼 앱이 계산 가능한 값은 결정적으로 계산한다.
- 수입 대비 지출, 전월 비교, 홈 인사이트 숫자는 앱이 계산한 context와 guard를 사용해 모델의 임의 산술을 제한한다.
- 일반 채팅은 최종 정책상 메시지당 1크레딧이며 local fast path 여부와 크레딧 정책은 별도 계약이다.
- Gemini client API key와 Claude BuildConfig/DataStore key 경로를 제거했다.
- 모든 Gemini 모델 생성은 `FirebaseAiModelFactory`를 통해 Firebase AI Logic으로 통합했다.
- release는 Play Integrity App Check, debug는 Debug App Check provider를 사용한다.
- rate limit policy와 category request circuit을 추가해 App Check 실패 뒤 대기 batch와 재진입 호출이 반복되지 않게 했다.
- 기본 운영 모델은 Flash Lite 중심의 RTDB model-name 설정을 사용하되 API key는 RTDB에서 읽지 않는다.

채팅의 local fast path, 3-step, Query/Action 계약은 [Chat System Contract](../chat/05-system-contract.md)를 본다.

## SMS·앱 알림 파싱

### 룰과 필터

- `app/src/main/assets/sms_rules_v1.json`의 sender·body regex 룰을 RTDB export 표본 기준으로 확대했다.
- 우리카드 앱 알림 중복, 카드 안내문, 승인 예정·청구·혜택 안내, 수입 오인식과 제외 카드 알림을 보정했다.
- `SmsNonTransactionNoticeFilter`, income filter/parser, semantic dedupe와 앱 알림 type classifier 회귀 테스트를 보강했다.
- regex 룰은 유효 named group, compile 가능성, asset integration과 표본 회귀 테스트를 통과해야 한다.
- 제공된 RTDB 표본 감사 결과는 192행 중 186행 처리(96.9%), 관측값 667/676 처리(98.7%)이며 조치 가능한 정책 오탐·미매칭은 0건이었다.

룰 수정 절차와 표본별 분석은 [SMS Rule JSON Guide](../sms-pipeline/06-rule-json-guide.md), [RTDB SMS Origin Import Log](../sms-pipeline/07-rtdb-sms-origin-import-log.md)를 본다.

### 원본 표본 수집 결정

- 표본 축적이 더 필요하다는 사용자 결정에 따라 `SmsOriginSampleCollector` 경로 자체는 유지한다.
- RTDB `send_origin_message=true`일 때만 `originBody`를 success/failure payload에 포함한다.
- `send_origin_message=false`이면 신규 payload에 원문을 넣지 않고 해당 fingerprint의 기존 `originBody` child도 제거한다.
- false 상태에서도 fingerprint, template 또는 masked body, parse source, count, 실패 단계 같은 비원문 품질 데이터는 계속 upsert한다.
- release 이후에는 `send_origin_message=false`를 유지한다는 결정이며, 이 값은 앱 업데이트 없이 RTDB에서 제어 가능하다.
- 외부 처리용 template·masked body는 `SmsSensitiveDataSanitizer`를 거치며 release 로그에는 문자 원문을 출력하지 않는다.
- SMS 권한 고지와 개인정보 처리방침은 기존 심사 통과 문구를 유지한다.

## 카테고리 분류와 임베딩

- 분류 순서는 Room 학습·StoreRule, vector, local keyword, Gemini batch의 local-first 구조다.
- `배달`을 `식비` 하위 leaf로 추가하고 화면·필터는 leaf 기준으로 처리한다.
- SMS·거래처 embedding을 결정적 local 768차원 vector로 교체했다.
- schema version/dimension이 다른 기존 Room vector는 최초 사용 시 현재 계약으로 재생성한다.
- App Check나 원격 서비스가 실패해도 StoreRule, vector, keyword와 `source=local` 저장은 계속 동작한다.
- 카테고리·수입 Gemini batch는 공유 permit으로 직렬화한다.
- 첫 App Check 인증 실패 후 15분 circuit을 열어 대기 batch, 2차 라운드, 수입 분류, resume 재시도를 원격 호출 없이 종료한다.

세부 tier와 임계값은 [Category Classification Tiers](../category-classification/06-classification-tiers.md), [Embedding Data Contract](../embedding/02-data-contract.md)를 본다.

## 동기화·삭제·재진입 안정화

- `ClassificationState`가 SMS sync, 자동 분류, 홈·설정 수동 분류의 단일 Job 소유권을 관리한다.
- sync 준비, DB 저장, coverage, 카드 자동 등록까지 소유 Job 안에서 완료한다.
- 잔여 미분류 처리는 sync 소유권 해제 뒤 시작해 completion race를 방지한다.
- 전체 삭제 gate는 중복 삭제를 직렬화하고 cancellation cleanup 동안 신규 sync·분류를 거부한다.
- gate epoch로 삭제 이전에 생성된 증분·월별 sync와 resume 검사를 무효화한다.
- 월별 sync는 실제 차감 결과를 추적해 시작 거부, 취소, 실패 시 차감된 1크레딧만 환불한다.
- 앱 resume 시 App Check cooldown 중 local-only 분류는 한 번만 허용한다.

Activity-scoped 상태와 화면 연결은 [App Shell KB](../app-shell/README.md)를 본다.

## UI와 접근성

- `font_scale=2.0`과 foldable closed/open 화면에서 홈, 내역, 필터, 검색, 거래 상세, 카테고리 상세, 채팅, 설정과 하위 화면을 순회했다.
- 홈·카테고리 누적 차트 양끝 라벨이 잘리지 않도록 표시 계약을 보완했다.
- Export JSON/CSV 선택은 최대 글자에서 세로 배치하고 일반 글자에서는 기존 밀도를 유지한다.
- 거래 날짜·시간은 최대 글자에서 전체 값이 보이도록 줄바꿈하고 일반 글자에서는 한 줄을 유지한다.
- 공통 카테고리 picker는 최대 글자에서 3열, 일반 글자에서 4열로 동작하며 전체 이름을 표시한다.
- AI 크레딧 화면, 채팅 오류·재요청, 설정 메뉴와 거래 상세 접근성을 함께 확인했다.

화면 함수와 설정 하위 메뉴는 [Composable Index](../ui-map/01-screen-composable-index.md), [Settings Menu Map](../settings/package-reference/05-menu-map.md)를 본다.

## 2026-07-12~13 최종 안정화 작업

| 작업 | 코드 결과 | KB 기록 |
|---|---|---|
| Firebase AI Logic 전환 | client key 제거, model factory·App Check provider 적용 | chat, embedding, project-context, worklogs |
| SMS 룰 재검증 | RTDB 표본 기반 asset·audit script·회귀 테스트 보강 | sms-pipeline |
| clean sync 비교 | 최근 2개월 492개 원본에서 지출 299건·수입 16건 생성, 7월 홈 집계 전후 일치 | worklogs |
| AI 실패 격리 | App Check circuit, batch 직렬화, local fallback 유지 | category-classification |
| sync 경쟁 조건 | 단일 Job, 삭제 gate, epoch, 잔여 분류 순서 보강 | app-shell |
| 크레딧·광고 | 실제 차감분 환불, SMS 권한 선확인, pending epoch 보존 | budget-credit-monetization |
| Cloud 설정 | Android Play Integrity `등록됨`, Firebase AI Logic `기본 - 적용됨` 확인 | worklogs |
| 최종 AVD | release APK 재설치, cold/hot, 설정 수동 분류, fatal/ANR 0 | worklogs |

## 검증 기준선

| 검증 | 결과 |
|---|---|
| Debug unit tests | 257개, 실패·오류·skip 0 |
| Release unit tests | 257개, 실패·오류·skip 0 |
| `lintRelease` | 0 errors, 199 warnings, 4 hints |
| release APK | 8,847,549 bytes, SHA-256 `35129257F5C345914A4E7C476DEE9288D79604A10FCC2674B0891297502CA25C` |
| release AAB | 15,037,075 bytes, SHA-256 `85FCE0517B8D61217AEEB3AA82D9D9242F24DAAEF4E19B2DF31DE2FEB18028F3` |
| APK packaging | v2 signing, 16KB zipalign 통과 |
| AAB packaging | `jar verified` 통과 |
| 실기기 clean sync | 초기화 전·후 7월 홈 지출·수입·전월 비교·TOP 4 정확히 일치 |
| 데이터 무결성 | smsId 중복 0, 금액·시간·거래처 중복 0, 0원 이하 0, 빈 거래처 0 |
| 최종 Android 16 AVD | `1.0.3(19)`, cold/hot 동일 PID, 수동 분류 추가 실패 0, fatal/ANR 0 |

전체 명령과 화면별 증거는 [Release Readiness Remediation Plan](../worklogs/02-release-readiness-remediation-plan.md)을 기준으로 한다.

## 배포 판정과 잔여 운영 확인

- 현재 소스와 로컬 산출물은 `develop` 병합 가능한 1.0.3 배포 후보로 판정했다.
- sideload AVD에서는 Play Integrity 특성상 `Firebase App Check token is invalid`가 예상되며, circuit이 반복 호출을 차단하는 것까지 검증했다.
- Play 서명 배포본의 실제 AI 정상 응답은 sideload로 대체할 수 없으므로 배포 직후 채팅 또는 홈 인사이트 1회를 호출해 확인한다.
- 크레딧 차감 직후 프로세스가 강제 종료되면 영속 request settlement가 없어 자동 환불하지 못하는 잔여 리스크가 있다. 현재 RTDB gate와 가상 크레딧 단계에서는 비차단이며 유료화 전에 영속 정산이 필요하다.
- Google Drive는 계정 선택 화면까지 검증했고 실제 계정 백업·복원은 별도 운영 검증이다.
- 실기기 조건이 필요한 월별 SMS 계측 테스트 2개는 AVD에서 skip되며 기존 clean sync와 수동 데이터 검증으로 보완했다.

## 관련 KB 라우팅

| 작업 | 먼저 읽을 문서 |
|---|---|
| 배포 품질과 검증 근거 | [Release Readiness Remediation Plan](../worklogs/02-release-readiness-remediation-plan.md) |
| SMS regex 수정 | [SMS Rule JSON Guide](../sms-pipeline/06-rule-json-guide.md) |
| RTDB 표본과 실패 원인 | [RTDB SMS Origin Import Log](../sms-pipeline/07-rtdb-sms-origin-import-log.md) |
| 문자 읽기·저장·알림 계약 | [SMS Ingestion Contract](../sms-parsing/06-ingestion-contract.md) |
| 카테고리 분류 | [Category Classification Tiers](../category-classification/06-classification-tiers.md) |
| 임베딩 | [Embedding Data Contract](../embedding/02-data-contract.md) |
| 채팅 | [Chat System Contract](../chat/05-system-contract.md) |
| 크레딧·광고 | [AI Credit Policy](../budget-credit-monetization/02-policy-and-plans.md) |
| 앱 진입·sync 상태 | [App Shell KB](../app-shell/README.md) |
| 금융 앱 알림 수신 | [Notification Ingestion](../notification-ingestion/01-feature-flow.md) |
| MoneyTalk 자체 거래 알림 | [Notification Display](../notification-display/01-feature-flow.md) |
| 권한·심사 정책 | [SMS Permission Policy](../onboarding/01-sms-permission-policy.md) |
| 화면 함수 | [Composable Index](../ui-map/01-screen-composable-index.md) |

## 커밋 목록

| 순서 | 커밋 | 날짜 | 요약 |
|---:|---|---|---|
| 1 | `a39bfdc` | 2026-06-02 | AI 크레딧 기반 채팅 보상 구조 추가 |
| 2 | `a60f227` | 2026-06-02 | 크레딧 이관과 광고 닫힘 처리 보완 |
| 3 | `1845f7a` | 2026-06-02 | 질문 유형별 크레딧 정책 추가 |
| 4 | `2ba6529` | 2026-06-02 | 상담 분류와 환불 보완 |
| 5 | `e81aee3` | 2026-06-04 | 우리카드 앱 알림 중복 파싱 보정 |
| 6 | `9486e16` | 2026-06-11 | SMS 안내문과 제외 카드 알림 보정 |
| 7 | `746f821` | 2026-06-15 | 크레딧 gate와 배달 카테고리 보완 |
| 8 | `832ab69` | 2026-06-15 | develop 버전 변경 병합 |
| 9 | `dbac99a` | 2026-07-03 | KB scaffold와 초기 KB 추가 |
| 10 | `b91e223` | 2026-07-03 | 기능별 KB 보강 |
| 11 | `105805e` | 2026-07-08 | KB scaffold 기준 보강 |
| 12 | `6cacfc6` | 2026-07-08 | Gemini 비용 방어와 local 단순 조회 추가 |
| 13 | `55676d1` | 2026-07-08 | 화면별·기능별 KB 확장 |
| 14 | `ab2e9a2` | 2026-07-08 | Chat 화면 KB 구조 보강 |
| 15 | `efecd83` | 2026-07-08 | KB 계획표 상태 보정 |
| 16 | `56bd3ea` | 2026-07-08 | 완료된 KB 계획표 제거 |
| 17 | `87c2406` | 2026-07-08 | 화면별 진입 경로 KB 추가 |
| 18 | `9c2ec83` | 2026-07-08 | 머지 전 리뷰 이슈 보완 |
| 19 | `4d5c3a3` | 2026-07-08 | AI 크레딧 사용 정책 단순화 |
| 20 | `8a3e553` | 2026-07-08 | 크레딧 동기화 KB routing 보강 |
| 21 | `35ca396` | 2026-07-09 | 루트 문서를 MoneyTalk KB로 통합 |
| 22 | `03e2e76` | 2026-07-10 | AI 크레딧 release 동작 보강 |
| 23 | `a89ac0b` | 2026-07-10 | release readiness 검증 기록 |
| 24 | `4ae7e1b` | 2026-07-12 | 1.0.3 AI·SMS release flow 보강 |
| 25 | `8e69a59` | 2026-07-12 | 1.0.3 release 검증과 KB 동기화 |
| 26 | `d332ca1` | 2026-07-12 | AI 실패와 sync race 격리 |
| 27 | `8b2de03` | 2026-07-12 | AI Logic App Check 적용 상태 기록 |
| 28 | `fa07dda` | 2026-07-13 | 최종 emulator release 검증 기록 |
