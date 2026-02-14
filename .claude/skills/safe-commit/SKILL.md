---
name: 커밋
description: 코드 변경 사항을 셀프 리뷰 후 안전하게 커밋한다. develop/master 브랜치 보호, 리뷰 후 수정 반영 포함.
argument-hint: "[커밋 메시지] (선택)"
---

# Safe Commit 스킬

커밋 요청 시 아래 절차를 **반드시 순서대로** 수행한다.

---

## 1. 브랜치 확인

```bash
git branch --show-current
```

- 현재 브랜치가 `develop` 또는 `master`이면 **즉시 새 기능 브랜치를 생성**하고 체크아웃한다.
- 브랜치명은 변경 내용에 맞게 `feature/`, `fix/`, `refactor/` 등으로 생성한다.
- **예외**: 사용자가 명시적으로 "develop에서 작업해" 또는 "develop에 커밋해"라고 지시한 경우에만 해당 브랜치에서 직접 커밋한다.

```bash
# 예시: develop에 있을 때
git checkout -b feature/작업내용
```

---

## 2. 변경 사항 확인

```bash
git status
git diff
```

- 변경된 파일 목록과 diff를 확인한다.

---

## 3. 셀프 리뷰

변경된 **모든 파일**을 `Read` 도구로 다시 읽고 아래 항목을 검토한다:

- [ ] 불필요한 코드 (디버깅용 로그, 주석 처리된 코드 등)
- [ ] 잘못된 로직 (조건 분기 오류, off-by-one 등)
- [ ] thread-safety 이슈 (불필요한 `withContext`, race condition 등)
- [ ] import 누락/미사용
- [ ] 기존 코드 스타일과의 불일치

---

## 4. 수정 사항 반영

셀프 리뷰에서 문제를 발견하면 **즉시 수정**한다.
수정 후 다시 `git diff`로 최종 상태를 확인한다.

---

## 5. 커밋

```bash
git add [변경된 파일들]
git commit -F .git/COMMIT_MSG
```

- 커밋 메시지는 `.git/COMMIT_MSG` 파일에 작성 후 `-F` 옵션으로 사용한다 (Windows cmd 따옴표 문제 방지).
- `git add -A`나 `git add .` 대신 **변경된 파일을 명시적으로 지정**한다.
- 민감 파일(.env, credentials 등)이 포함되지 않았는지 확인한다.

### 커밋 메시지 규칙

```
type: 한줄 요약

- 상세 변경 내용 1
- 상세 변경 내용 2

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

type: `feat`, `fix`, `refactor`, `docs`, `chore` 등

---

## 6. 완료 보고

커밋 결과를 사용자에게 보고한다:
- 브랜치명
- 커밋 해시
- 변경 파일 수 / 추가 / 삭제 라인 수

> 푸시는 사용자가 요청한 경우에만 수행한다.
