---
title: "MoneyTalk 현재 기준 KB 검증 기록"
status: verified
last_checked: "2026-09-30"
source_ref: "95b419e8b98e374a991f3210a194a88959937ee8"
---

# MoneyTalk 현재 기준 KB 검증 기록

## 범위

현재 기준 묶음의 링크·메타데이터·placeholder·소스 근거·대표 질문 탐색을 확인한다. 기존 KB 전체의 의미 검증이나 KB 사용 효과의 통계적 비교 실험은 아니다. app 기능코드 변경은 없다.

## 검증 계획

독립 탐색자는 작성 대화를 상속하지 않은 새 하위 에이전트에서 이 묶음의 README부터 시작한다. 질문마다 필요한 파일과 심볼, 연결 흐름, 중요한 제약, 미검증 범위를 코드와 대조한다. 질문 6개를 실행 전에 고정하고 각 질문의 필수 판단·근거·제약을 모두 찾았을 때만 통과로 기록한다.

| ID | 대표 개발 질문 |
|---|---|
| Q1 | 앱 최초 진입과 재개 시 SMS 동기화는 어디서 시작하며 권한 거절은 어떻게 처리되는가? |
| Q2 | 배치 SMS와 실시간 SMS의 저장·중복 방지·화면 갱신을 바꾸려면 어떤 파일을 함께 봐야 하는가? |
| Q3 | 자동 거래처 카테고리 선택 순서와 embedding 생성 위치는 어디이며 정확도를 여기서 확정할 수 있는가? |
| Q4 | 로컬 채팅 조회는 언제 모델 호출을 생략하며 항상 무료인가? |
| Q5 | 채팅 액션 실행 후 final 답변이 실패하면 거래와 크레딧은 어떤 경계에서 확인해야 하는가? |
| Q6 | Room schema·빌드 설정을 바꾸려면 어디를 보며 이 환경에서 Firebase 연결과 Android 테스트가 성공했는가? |

접근은 README·data-flows·development 및 거기서 연결한 소스만 권고한다. 파일 접근 격리는 강제하지 않으며 탐색자 보고와 새 도구 기록으로 확인한다. 정답표·다른 탐색자의 답은 제공하지 않는다. 모델별 속도/token/비용을 측정하지 않고 무KB 기준선, 반복 비교, Full/Lean 비교도 수행하지 않는다. 따라서 효과 개선률을 주장하지 않는다.

## 자동 검사

ClaudeGuide root에서 실행:

```bash
python3 docs/kb-scaffold/scripts/validate_scaffold.py --root docs/kb-scaffold --mode toolkit
python3 -m unittest discover -s docs/kb-scaffold/scripts -p 'test_*.py'
```

프로젝트 root에서 실행(검증기 경로는 별도 ClaudeGuide checkout):

```bash
python3 ../ClaudeGuide/docs/kb-scaffold/scripts/validate_scaffold.py \
  --root docs/moneytalk-kb/project-context/current-baseline --mode kb --project-root .
python3 ../ClaudeGuide/docs/kb-scaffold/scripts/validate_scaffold.py \
  --root docs/moneytalk-kb --mode kb --project-root .
git diff --check
```

KB의 링크는 다른 checkout을 가리키지 않는다. 위 명령의 `../ClaudeGuide`는 도구 실행 위치만 뜻한다.

| 검사 | 실제 결과 |
|---|---|
| 공용 toolkit | 28 Markdown, 0 errors; exit 0 |
| 공용 검증기 회귀 테스트 | 37 tests, OK; exit 0 |
| 기존 전체 KB 변경 전 | 210 Markdown, 744 errors; exit 1 |
| 새 기준 묶음 | 5 Markdown, 0 errors; exit 0. 탐색 중 발견 보정 후 재검사도 0 errors |
| 변경 문서 전체 보충 검사 | 수정 6개+생성 6개, 상대 링크 322개, 메타데이터/링크 오류 0. 프로젝트 README에는 KB frontmatter 계약을 적용하지 않음 |
| 기존 전체 KB 통합 후 | 216 Markdown, 723 errors; exit 1. 신규 묶음 오류 없음; 기존 오류를 성공으로 숨기지 않음 |
| diff·placeholder·설정값 검토 | `git diff --check` exit 0; 새 묶음 template braces/TODO/TBD/FIXME 및 비밀 형태 0. 변경 내용을 읽고 실제 설정값·개인정보 미포함 확인 |

기존 오류 744개는 검사기의 단일 행 메타데이터 규약과 구형 frontmatter의 차이도 포함한다. `frontmatter-syntax` 172, `last_checked` 누락 176, `source_ref` 누락 176, README 필수 섹션 누락 176(44×4), verified 근거/검증 내용 누락 29, frontmatter/status 누락 8, 깨진 대상 링크 5·anchor 1, 루트 change-log 누락 1이다. 이 수를 앱 결함 수로 해석하지 않는다. 과거 실행 캡처를 가리키는 5개 누락 링크와 anchor 오류는 기존 프로젝트 컨텍스트 검증 문서에 있었으며 해당 캡처를 만들어내지 않는다.

## 대표 질문 결과

최초 탐색자는 다른 에이전트의 완료 답변이 도구 출력에 노출되어 무효 처리했다. 소스를 읽기 전 발견했으며 해당 실행의 답을 채점하지 않았다. 작성 대화를 상속하지 않은 새 탐색자가 질문 6개를 다시 대조했다. 접근 목록은 탐색자 자기보고이며 프로젝트/KB/테스트 57개와 작성 계약 2개, 총 59개 파일의 전체/구간/심볼 검색을 포함한다. 실행·네트워크 검증은 하지 않았다.

| ID | 발견한 소스·심볼과 핵심 제약 | 최초 유효 평가 |
|---|---|---|
| Q1 | Manifest/Intro의 `navigateToMain`, Main의 `onResume` → `onAppResume`; Intro 권한 거절 후 Main 이동과 READ_SMS 동기화 gate는 구분 | 통과 |
| Q2 | `syncSmsV2Internal`/writer snapshot, 실시간 `processAndSave`, Deferred fallback, DAO/삭제 추적/refresh 연결. `launchSync` 초기 refresh는 변경 건수와 무관한 예외 | 부분 실패: 문서가 초기 refresh 조건을 좁게 설명 |
| Q3 | 일반/캐시/writer/Gemini 분류 모드와 `SmsEmbeddingService.createEmbedding`의 로컬 n-gram; 임계값은 정확도 증거가 아님 | 통과 |
| Q4 | `tryRoute` → query executor → `saveLocalExchange`; `sendMessage`의 크레딧 gate가 로컬 분기보다 앞 | 통과; 원장 직접 링크 부족 발견 |
| Q5 | `ChatActionExecutor.execute`가 final 호출보다 먼저; 환불과 거래 rollback/재시도는 다른 경계 | 통과; 환불 실제 반영은 미검증 |
| Q6 | AppDatabase v8/19 entities와 DI migration 등록, SDK/Gradle 설정; wrapper 실패 관측과 실제 Firebase/Android 성공을 구분 | 통과 |

Q2 설명은 `launchSync`와 `syncSmsV2` 공통 내부 흐름을 명시하고, 최초 동기화 `notifyDataChanged` 무조건 호출과 그 밖의 `handleSyncResult.hasDataChange` 분기로 수정했다. 수입 `IncomeRepository`/`IncomeDao`와 크레딧 `RewardAdManager` → `AiCreditRepository` → `AiCreditDao` 직접 링크를 추가했다. root가 실제 정의·호출과 정적 링크를 다시 대조했고 공용 검사 오류 0을 확인했다. 최초 부분 실패와 수정 내역을 이후 성공으로 지우지 않는다.

평가 질문 JSON의 SHA-256은 `b4ed155bc0c72a40461b082559625e904a366b0788114fbc0fc0d4b797bd3d38`이다. 최초 유효 평가에 제공한 draft 문서 해시는 아래와 같으며 후속 보정·상태 전환 전 기준이다. 결과를 본 뒤 질문/성공 기준을 바꾸지 않았다.

| 문서 | 평가 입력 SHA-256 |
|---|---|
| README.md | `0a65dd6cb31c200e843c893bf3d569a12814ec643e35a935d556af24bad643fb` |
| data-flows.md | `8f3154142e379df9c8b2f66a608f7339929f3fee92a8c3e2308cbb988a6aa4cc` |
| development.md | `3a6d3b4953f3b5e71a256001775c3954456d0353805f95a66ad474733851d164` |

## 근거

코드 기준 `95b419e8b98e374a991f3210a194a88959937ee8`, 공용 toolkit 기준 `2d2aa90c82a141e7440d3ee60b692911eb2ee5f1`; 후자는 실제 HEAD와 일치하고 `git merge-base --is-ancestor` exit 0을 확인했다. 두 저장소는 조사 시작 시 작업트리가 깨끗했고 사용자 변경을 stash/reset/clean하지 않았다. 앱 소스 변경 없이 문서만 작성한다.

근거 대조 진입점은 [현재 기준 README](README.md), [데이터 흐름](data-flows.md), [개발·실행](development.md)의 상대 소스 링크·심볼이다. SHA 존재 검사는 문서 의미 정확성을 대신하지 않는다.

## 검증

문서의 `verified`는 적힌 코드 기준의 정적 대조와 탐색 완료를 뜻한다. Android 빌드·unit test 시도는 wrapper lock 디렉터리 생성 실패로 exit 1이며 컴파일 전 중단됐다. lint, 기기 테스트/설치/실행, 실제 SMS/Firebase/AI/광고/migration 검증은 미실행이다. [개발·실행](development.md)의 환경 관측과 재실행 명령을 따른다.
