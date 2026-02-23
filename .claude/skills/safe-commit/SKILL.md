---
name: 커밋
description: 코드 변경 사항을 셀프 리뷰 후 안전하게 커밋한다. 작업별 분리 커밋 + develop/master 브랜치 보호.
argument-hint: "[커밋 메시지] (선택)"
---

# Safe Commit 스킬

> **규칙 SSOT**: 프로젝트에 `docs/GIT_CONVENTION.md`가 있으면 해당 문서를 따른다.
> 없으면 이 스킬의 기본 규칙을 따른다.

커밋 요청 시 아래 절차를 **반드시 순서대로** 수행한다.

---

## 1. 브랜치 확인

```bash
git branch --show-current
```

- `develop` 또는 `master`이면 → **새 브랜치 생성 후 체크아웃**
- 예외: 사용자가 명시적으로 해당 브랜치에서 작업하라고 지시한 경우

---

## 2. 변경 사항 확인 + 분리 계획

```bash
git status
git diff --stat
```

- 변경 파일을 **목적별로 그룹핑**한다
- 서로 다른 목적(기능/버그수정/문서/Lint 등)은 **별도 커밋으로 분리**

---

## 3. 셀프 리뷰

변경된 **모든 파일**을 `Read` 도구로 다시 읽고 검토:

- 불필요한 코드 (디버깅 로그, 주석 처리된 코드)
- 잘못된 로직 (조건 분기 오류, off-by-one)
- thread-safety 이슈 (불필요한 `withContext`, race condition)
- import 누락/미사용
- 기존 코드 스타일과의 불일치

문제 발견 시 **즉시 수정** 후 `git diff`로 재확인.

---

## 4. 그룹별 커밋

각 그룹에 대해:

```bash
git add [그룹에 해당하는 파일들]
git commit -m "$(cat <<'EOF'
[타입]: 제목

- 상세 변경 내용

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

- `git add -A` 사용 금지 → 파일 명시적 지정
- 민감 파일(.env, credentials) 포함 여부 확인
- 커밋 메시지 타입: 프로젝트 GIT_CONVENTION.md가 있으면 따르고, 없으면 아래 기본값 사용
  - 한글: 기능/수정/개선/리팩토링/문서/스타일/테스트/설정/기타
  - 영문: feat/fix/refactor/docs/chore/style/test

---

## 5. 푸시

- 사용자가 "푸시" 또는 "커밋푸시"를 요청한 경우에만 수행
- 원격 추적 없는 브랜치: `git push -u origin {브랜치명}`

---

## 6. 완료 보고

커밋 결과를 사용자에게 보고:
- 브랜치명
- 커밋 목록 (해시 + 제목)
- 총 변경 파일 수
