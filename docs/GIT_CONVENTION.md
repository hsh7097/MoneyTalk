# Git 컨벤션

## 커밋 메시지 규칙

### 언어
- **한글**로 작성

### 형식
```
[타입]: 제목

본문 (선택)

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

### 타입
| 타입 | 설명 |
|------|------|
| 기능 | 새로운 기능 추가 |
| 수정 | 버그 수정 |
| 개선 | 기존 기능 개선 |
| 리팩토링 | 코드 구조 변경 (기능 변화 없음) |
| 문서 | 문서 추가/수정 |
| 스타일 | 코드 포맷팅, 세미콜론 등 |
| 테스트 | 테스트 코드 추가/수정 |
| 설정 | 빌드, 설정 파일 변경 |
| 기타 | 위에 해당하지 않는 경우 |

### 예시
```
기능: SMS 권한 요청을 앱 시작 시 표시

- 홈 화면 로딩 버튼 대신 앱 진입 시 권한 요청
- PermissionHandler 클래스 추가
- MainActivity에서 권한 체크 로직 구현

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

```
수정: HomeViewModel suspend 함수 호출 오류 해결

- setApiKey() 호출을 viewModelScope.launch로 감싸기
- hasApiKey()를 콜백 패턴으로 변경

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

---

*마지막 업데이트: 2026-02-05*
