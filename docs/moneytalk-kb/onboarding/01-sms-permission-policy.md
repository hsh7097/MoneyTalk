---
type: policy
title: SMS Permission Policy
description: MoneyTalk의 SMS 권한 요청 사유, 데이터 처리 고지, Play Console 릴리스 게이트를 정리한다.
tags: [moneytalk, onboarding, sms, permission, play-console]
resource: app/src/main/java/com/sanha/moneytalk/feature/intro/ui/PermissionScreen.kt
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# 01 SMS Permission Policy

> 기준: 2026-07-11 현재 SMS 권한 선언, Intro permission screen, 외부 AI 전송 최소화, privacy disclosure 확인

MoneyTalk는 SMS 기반 자동 가계부 생성을 핵심 기능으로 제공하기 때문에 `READ_SMS`, `RECEIVE_SMS` 권한을 요청한다.
권한 요청 UI, 개인정보 고지, Play Console 선언, Data safety 내용은 실제 데이터 흐름과 항상 맞아야 한다.

## 요청 권한

| 권한 | 용도 |
|---|---|
| `android.permission.READ_SMS` | 카드사/은행/결제 서비스 금융 SMS를 읽어 기존 거래를 가져온다. |
| `android.permission.RECEIVE_SMS` | 새로 수신된 금융 SMS를 감지해 거래로 저장한다. |

Google Play 권한 예외 카테고리는 SMS-based money management, 예시는 budget tracking app이다.
이 앱은 일반 SMS inbox, 번역, 연락처 프로파일링, 소셜 그래프 분석, 마케팅 조사, SMS 알림 강화 앱이 아니다.

## 핵심 기능 설명

MoneyTalk의 핵심 기능은 금융 SMS에서 금액, 거래처/출처, 결제/입금 시각, 카드/계좌 맥락을 추출해 사용자 가계부 거래를 자동 생성하는 것이다.
SMS 접근이 없으면 사용자는 카드 결제, 은행 입금, 이체 내역을 직접 입력해야 하므로 앱의 자동 추적 경험이 사라진다.

## SMS에서 수집하는 데이터

| 데이터 | 사용처 |
|---|---|
| 문자 본문 | 금액, 거래처, 거래 유형, 메모/원문 참조 추출 |
| 발신 번호 | 금융 발신자 판별과 sender regex 룰 적용 |
| 수신 시각 | 거래 시각 보정, 중복 방지 |
| 파싱된 금융 필드 | 지출/수입/이체 저장, 카테고리 분류, 요약/통계 표시 |

거래 데이터는 앱 로컬 DB에 저장된다.
Google Drive 백업은 사용자가 직접 시작한 경우 사용자의 Drive 계정에 저장된다.
로컬 파싱이 실패한 금융 SMS는 자동 기록을 위해 거래처명, 금액, 거래 맥락을 Gemini API로 보낼 수 있다.
전송 전 `SmsSensitiveDataSanitizer`가 사용자명과 계좌/카드 식별정보를 가능한 제거하거나 최소화한다.
`SmsOriginSampleCollector`는 파싱 룰 개선을 위해 마스킹된 표본을 RTDB에 저장한다.
RTDB `/config/send_origin_message=true`인 기간에는 원본 금융 SMS도 함께 저장하며, 충분한 표본을 확보하면 이 값을 `false`로 전환한다.
`false` 전환은 신규 원문 저장을 막지만 기존 row 전체를 일괄 삭제하지는 않으므로, 룰 검증 후 기존 `originBody` 삭제 절차를 별도로 수행한다.
SMS 데이터는 판매하거나 광고 목적으로 쓰지 않는다.

## 앱 내 고지 표면

| 표면 | 확인 항목 |
|---|---|
| Intro permission screen | `permission_sms_description`, `permission_note`가 SMS 접근 목적과 제한 사용을 설명하는가? |
| Privacy policy dialog | `privacy_collect_detail`, `privacy_usage_detail`, `privacy_storage_detail`, `privacy_thirdparty_detail`이 실제 흐름과 맞는가? |
| Privacy policy HTML | `docs/privacy-policy.html`이 SMS, Drive backup, Gemini, Firebase/AdMob 사용을 빠뜨리지 않는가? |
| MainActivity 권한 요청 | 시스템 권한 요청 전에 앱 자체 고지가 먼저 보이는가? |

권한을 거부해도 제한적인 수동 입력 기능을 사용할 수 있다는 점을 고지한다.

## 릴리스 게이트

프로덕션 릴리스에 SMS 권한이 포함되어 있으면 아래가 모두 완료되어야 한다.

1. Play Console Permissions Declaration Form에서 SMS-based money management use case를 선택한다.
2. 스토어 listing에 금융 SMS 기반 자동 가계부/예산 추적이 핵심 기능임을 명확히 적는다.
3. 앱 내 권한 고지와 개인정보 처리방침이 실제 데이터 흐름과 일치한다.
4. Data safety에 SMS 접근, 로컬 저장, AI 처리, Google Drive 백업, Firebase/AdMob 사용, 사용자 삭제 제어를 반영한다.
5. 새 SMS 처리 경로가 추가되면 [../sms-parsing/README.md](../sms-parsing/README.md), [../sms-pipeline/README.md](../sms-pipeline/README.md), [../notification-ingestion/README.md](../notification-ingestion/README.md)의 데이터 흐름도 함께 확인한다.
6. `send_origin_message` 운영값, 원문 수집 기간, 기존 `originBody` 삭제 계획을 확인하고 원문 SMS가 release warning/error 로그에 남지 않는지 확인한다.

## 수정 시 주의

- 권한 설명 문구만 바꾸고 실제 수집/전송 경로를 놓치면 Play Console 선언과 불일치한다.
- Gemini 전송 데이터가 늘어나면 개인정보 고지와 prompt/data minimization 정책을 함께 본다.
- 금융앱 알림 수신 기능은 SMS 권한과 별개로 알림 접근/후보 승인 경로가 있으므로 [../notification-ingestion/README.md](../notification-ingestion/README.md)를 같이 확인한다.
- 자체 거래 알림 표시는 데이터 수신이 아니라 사용자에게 저장 결과를 보여주는 기능이므로 [../notification-display/README.md](../notification-display/README.md)와 구분한다.
