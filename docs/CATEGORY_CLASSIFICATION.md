# 카테고리 자동 분류 시스템

> 가게명 기반 4-Tier 카테고리 분류: Room 매핑 → 벡터 유사도 → 로컬 키워드 → Gemini 배치

---

## 1. 시스템 개요

MoneyTalk은 SMS에서 추출한 가게명을 기반으로 지출 카테고리를 자동 분류합니다.
4단계로 점진적으로 분류하며, 벡터 캐싱과 자가 학습으로 시간이 지날수록 정확도가 높아집니다.

```
가게명 입력 (예: "스타벅스역삼점")
  |
  v
+--------------------------------------+
| Tier 1: Room DB 정확 매핑 조회         | <-- 비용 0, 즉시
|  CategoryMappingEntity 검색           |
+--------+--------------+--------------+
    발견  |              | 미발견
         v              v
    즉시 반환    +-------------------------------+
                | Tier 1.5: 벡터 유사도 매칭      | <-- 임베딩 API 1회
                |  StoreEmbeddingEntity 검색     |
                |  코사인 유사도 >= 0.92          |
                +--------+--------------+-------+
                    매칭  |              | 미매칭
                         v              v
                  캐시 프로모션  +---------------------------+
                  (Room 저장)   | Tier 2: 로컬 키워드 매칭    | <-- 비용 0, 즉시
                  + 즉시 반환    |  SmsParser.inferCategory  |
                                |  250+ 키워드 사전          |
                                +--------+----------+------+
                                    매칭  |          | "기타"
                                         v          v
                                   Room 저장   +-------------------------+
                                   + 반환       | Tier 3: Gemini 배치 분류  | <-- 별도 트리거
                                                |  시맨틱 그룹핑 최적화    |
                                                |  대표만 Gemini 전송      |
                                                +--------+---------------+
                                                    분류  |
                                                         v
                                                  Room + 벡터 DB 저장
                                                  + 지출 업데이트
```

### Tier별 비용/속도 비교

| Tier | 커버리지 | 비용 | 속도 |
|------|---------|------|------|
| 1: Room 매핑 | 이전 분류 결과 | 무료 | <1ms |
| 1.5: 벡터 유사도 | 유사 가게명 (스타벅스강남 -> 스타벅스역삼) | 임베딩 API 1회 | ~1초 |
| 2: 로컬 키워드 | 키워드 매칭 가능한 가게 | 무료 | <1ms |
| 3: Gemini 배치 | 나머지 전부 | Gemini API 1회 | ~2초 |

---

## 2. Tier 1: Room DB 매핑 (캐시)

### 파일 위치
- `core/database/entity/CategoryMappingEntity.kt`
- `core/database/dao/CategoryMappingDao.kt`
- `feature/home/data/CategoryRepository.kt`

### category_mappings 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 증가 |
| storeName | String (UNIQUE) | 가게명 (정규화된 형태) |
| category | String | 카테고리 displayName |
| source | String | 분류 출처: `local`, `gemini`, `user`, `vector` |
| createdAt | Long | 생성 시간 |
| updatedAt | Long | 수정 시간 |

### 분류 출처 (source)
| 값 | 의미 |
|----|------|
| `local` | SmsParser 로컬 키워드로 자동 분류 |
| `gemini` | Gemini API로 AI 분류 |
| `user` | 사용자가 수동으로 카테고리 변경 |
| `vector` | 벡터 유사도 매칭으로 자동 분류 (캐시 프로모션) |

---

## 3. Tier 1.5: 벡터 유사도 매칭 (NEW)

### 파일 위치
- `core/database/entity/StoreEmbeddingEntity.kt`
- `core/database/dao/StoreEmbeddingDao.kt`
- `feature/home/data/StoreEmbeddingRepository.kt`
- `core/util/VectorSearchEngine.kt` (findBestStoreMatch)

### store_embeddings 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 증가 |
| storeName | String (UNIQUE INDEX) | 가게명 |
| category | String | 분류된 카테고리 |
| embedding | List<Float> -> JSON | 768차원 임베딩 벡터 |
| source | String | 분류 출처: `gemini`, `user`, `propagated` |
| confidence | Float | 신뢰도 (0.0~1.0) |
| matchCount | Int | 매칭 횟수 (사용 빈도 추적) |
| createdAt | Long | 생성 시간 |
| updatedAt | Long | 마지막 업데이트 시간 |

### 동작 원리
1. 가게명의 임베딩 벡터 생성 (Gemini Embedding API 1회)
2. 인메모리 캐시에서 코사인 유사도 검색
3. 유사도 >= 0.92이면 해당 카테고리 반환

### 캐시 프로모션
벡터 매칭 성공 시, 해당 가게명을 Room 정확 매핑(Tier 1)에도 저장합니다.
다음 조회 시 Tier 1에서 즉시 반환되어 임베딩 API 호출도 불필요합니다.

### 유사도 임계값

| 상수 | 값 | 용도 |
|------|---|------|
| `STORE_SIMILARITY_THRESHOLD` | 0.92 | 카테고리 자동 적용 |
| `PROPAGATION_SIMILARITY_THRESHOLD` | 0.90 | 사용자 수정 전파 |
| `GROUPING_SIMILARITY_THRESHOLD` | 0.88 | 시맨틱 그룹핑 |

---

## 4. Tier 2: 로컬 키워드 매칭 (SmsParser.inferCategory)

### 파일 위치
`core/util/SmsParser.kt` -- `inferCategory()` 메소드

### 카테고리별 키워드 사전 (250+ 키워드)

| 카테고리 | 대표 키워드 | 세부 분류 |
|---------|-----------|---------|
| 식비 | GS25, 이마트, 맥도날드 등 | 고기, 일식, 중식, 한식, 치킨, 피자, 패스트푸드, 분식, 편의점, 마트 |
| 카페 | 스타벅스, 이디야 등 | 카페, 베이커리, 아이스크림/빙수 |
| 배달 | 배달의민족, 요기요, 쿠팡이츠 등 | 배달앱 전용 (Gemini 프롬프트에서도 배달앱은 반드시 "배달"로 분류) |
| 교통 | 카카오T, KTX 등 | 택시, 대중교통, 주유, 주차 |
| 쇼핑 | 쿠팡, 무신사 등 | 온라인쇼핑, 패션, 뷰티, 생활용품 |
| 구독 | 넷플릭스, 유튜브 등 | 구독, 정기결제, 멤버십 |
| 의료/건강 | 병원, 약국 등 | 병원, 약국 |
| 운동 | 헬스, 필라테스 등 | 피트니스, 요가, PT |
| 문화/여가 | CGV, 야놀자 등 | 영화, 놀이공원, 게임/오락, 여행/숙박, 공연/전시 |
| 교육 | 학원, 교보문고 등 | 교육, 도서 |
| 생활 | SKT, 보험 등 | 통신, 공과금, 보험, 미용 |
| 기타 | -- | 위 키워드에 매칭 안 됨 |

---

## 5. Tier 3: Gemini 배치 분류 (시맨틱 그룹핑)

### 파일 위치
- `feature/home/data/GeminiCategoryRepository.kt` -- Gemini API 호출
- `feature/home/data/CategoryClassifierService.kt` -- 분류 오케스트레이터
- `core/util/StoreNameGrouper.kt` -- 시맨틱 그룹핑

### 시맨틱 그룹핑 최적화

미분류 가게명을 벡터 유사도로 그룹핑한 후, 각 그룹의 대표만 Gemini에 전송합니다.

```
미분류 100개 가게명
  |
  v
StoreNameGrouper.groupStoreNames()
  |-- 배치 임베딩 생성 (50개씩)
  |-- 그리디 클러스터링 (유사도 >= 0.88)
  |
  v
60개 그룹 (40% 절감)
  |-- 대표 60개만 Gemini 전송
  |-- 분류 결과를 멤버에게 전파
  |
  v
Room + 벡터 DB에 저장
```

### 반복 분류 전략
- 최대 10라운드 반복 (무한 루프 방지)
- 라운드 간 2초 딜레이 (Rate Limit 방지)
- 더 이상 진전이 없으면 조기 종료

---

## 6. 자가 학습 피드백 루프

### Gemini 분류 결과 캐싱
Gemini가 분류한 결과를 벡터 DB에도 저장합니다.
다음에 유사한 가게명이 들어오면 Tier 1.5에서 즉시 반환됩니다.

### 사용자 수동 분류 + 전파
```
사용자가 "스타벅스강남점"을 "카페"로 수정
  |
  v
1. Room 매핑 업데이트 (source = "user")
2. 벡터 DB 업데이트 (source = "user", confidence = 1.0)
3. 유사 가게 전파 (유사도 >= 0.90)
   |-- "스타벅스역삼점" -> "카페" (source = "propagated")
   |-- "스타벅스서초점" -> "카페" (source = "propagated")
   |-- source="user"인 가게는 덮어쓰지 않음
```

### 우선순위
`user` > `propagated` > `vector` > `gemini` > `local`

---

## 7. AI 채팅에서의 카테고리 변경

채팅에서 자연어로 카테고리 변경을 요청할 수 있습니다:

| 사용자 요청 | 실행되는 액션 |
|-----------|------------|
| "쿠팡 결제는 쇼핑으로 분류해줘" | `update_category_by_store` (정확히 일치) |
| "배달의민족 포함된건 식비로 바꿔줘" | `update_category_by_keyword` (부분 일치) |
| "ID 123번 지출을 교통으로 변경해줘" | `update_category` (개별 변경) |

---

## 8. 카테고리 목록 (Category enum)

| Enum | 이모지 | displayName |
|------|--------|-------------|
| FOOD | -- | 식비 |
| CAFE | -- | 카페 |
| DRINKING | -- | 술/유흥 |
| TRANSPORT | -- | 교통 |
| SHOPPING | -- | 쇼핑 |
| SUBSCRIPTION | -- | 구독 |
| HEALTH | -- | 의료/건강 |
| FITNESS | -- | 운동 |
| CULTURE | -- | 문화/여가 |
| EDUCATION | -- | 교육 |
| HOUSING | -- | 주거 |
| LIVING | -- | 생활 |
| DELIVERY | -- | 배달 |
| EVENTS | -- | 경조 |
| ETC | -- | 기타 |

---

*마지막 업데이트: 2026-02-08*
