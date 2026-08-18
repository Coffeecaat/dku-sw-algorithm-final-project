# Cafe Map Algorithm Lab

검색, 지도 영역 필터링, HOT 랭킹을 대상으로 **같은 기능을 서로 다른 알고리즘과 처리 레이어로 구현했을 때 성능과 결과 품질이 어떻게 달라지는지 비교하는 Spring Boot 백엔드 프로젝트**입니다.

단순히 가장 빠른 응답만 찾는 것이 아니라 다음 두 요소를 함께 측정합니다.

- 성능: latency, p50/p95/p99, query count, 처리량
- 결과 품질: `avgSearchScore`, `avgHotScore`, `topHotCoverage`

Redis, 외부 검색 엔진, CDN, 추천 시스템 등의 외부 최적화 요소는 제외하고 Application/Service Layer와 Repository/DB Layer의 처리 방식 차이에 집중했습니다.

## 핵심 기능

| 기능 | 설명 |
| --- | --- |
| 카페 검색 | 카페명, 주소, 도로명주소, 메뉴명을 기준으로 검색하고 관련도 점수를 계산합니다. |
| 지도 마커 조회 | 현재 지도 `bounds` 안의 카페를 조회하고 zoom에 따른 개수 제한을 적용합니다. |
| HOT 랭킹 | HOT 점수, 평점 평균, 평점 수를 기준으로 우선 노출할 카페를 선택합니다. |
| 전략 비교 | 동일한 API에서 `strategy` 파라미터로 알고리즘을 바꿔 성능과 품질을 비교합니다. |

## 검색 전략

| 전략 | 처리 방식 | 특징 |
| --- | --- | --- |
| `APP_CONTAINS` | 후보를 넓게 조회한 뒤 Application에서 선형 탐색과 문자열 `contains` 수행 | 구현은 단순하지만 후보 증가 시 JVM 메모리와 CPU 비용이 증가합니다. |
| `DB_LIKE` | Repository/DB Layer에서 `LIKE` 조건으로 후보 축소 | Application으로 전달되는 row 수를 줄입니다. |
| `NORMALIZED` | 검색어와 대상 문자열을 소문자 변환, trim, 공백 제거 후 비교 | 성능보다는 입력 차이에 덜 민감한 정확도 개선 전략입니다. |

검색 결과는 다음 가중치를 사용해 `searchScore`를 계산하고 내림차순으로 정렬합니다.

| 항목 | 가중치 |
| --- | ---: |
| 카페명 정확 일치 | +100 |
| 카페명 포함 | +60 |
| 메뉴명 포함 | +40 |
| 주소/도로명주소 포함 | +20 |
| HOT 점수 | `hotScore * 0.10` |
| 평점 평균 | `ratingAverage * 2.0` |
| 평점 수 | `min(ratingCount, 200) * 0.05` |

## 지도 마커 전략

| 전략 | 처리 방식 | 품질/성능 특성 |
| --- | --- | --- |
| `UNSORTED_LIMIT` | 정렬 없이 limit을 먼저 적용 | 빠를 수 있지만 HOT 상위 후보를 보장하지 않습니다. |
| `HOT_RANK_LIMIT` | 후보별 metric 조회 후 HOT 정렬과 limit 적용 | HOT 품질은 유지하지만 N+1 query가 발생합니다. |
| `BATCH_METRIC_HOT_RANK` | 대표 메뉴와 metric을 batch 조회하고 Application에서 정렬 | N+1을 제거하지만 후보 전체를 JVM 메모리에 적재합니다. |
| `DB_HOT_RANK_LIMIT` | DB projection query에서 join, `ORDER BY`, `LIMIT` 수행 | Application에는 제한된 row만 전달되며 query count와 메모리 사용량을 줄입니다. |

`BATCH_METRIC_HOT_RANK`와 `DB_HOT_RANK_LIMIT`은 모두 HOT 상위 결과를 보존하지만 공간 사용 방식이 다릅니다. 전자는 Application 공간 복잡도가 `O(N)`, 후자는 최종 전달 데이터 기준 `O(limit)`에 가깝습니다.

## 기술 스택

- Java 21
- Spring Boot 3.3.5
- Spring Data JPA / Hibernate
- Flyway
- PostgreSQL 16 + PostGIS 3.4
- MySQL 8.4
- H2
- JUnit 5 / MockMvc / Hibernate Statistics
- k6
- Docker Compose

PostGIS 확장 migration은 포함되어 있지만 현재 조회 알고리즘에서는 PostGIS 공간 연산을 사용하지 않습니다.

## 프로젝트 구조

```text
.
├─ backend/
│  ├─ src/main/java/com/example/cafemap/
│  │  ├─ cafe/       # 지도 마커 조회와 HOT 랭킹
│  │  ├─ search/     # 검색 API와 검색 전략
│  │  ├─ menu/       # 메뉴 도메인과 repository
│  │  ├─ metric/     # HOT 점수와 평점 통계
│  │  └─ common/     # 공통 설정과 예외 처리
│  └─ src/test/      # API, 통합, DB 성능 테스트
├─ docs/performance/
│  └─ search-and-marker.k6.js
└─ infra/
   └─ docker-compose.yml
```

## 실행 환경

필수 도구:

- JDK 21
- Docker Desktop 또는 Docker Engine + Compose
- k6 (부하 테스트 실행 시)

### 1. DB 실행

프로젝트 루트에서 PostgreSQL과 MySQL을 실행합니다.

```powershell
docker compose -f infra/docker-compose.yml up -d
```

기본 로컬 연결 정보는 실험 전용 값입니다.

| DB | 주소 | Database/User/Password |
| --- | --- | --- |
| PostgreSQL | `localhost:55432` | `cafe_map_lab` |
| MySQL | `localhost:3307` | `cafe_map_lab` |

### 2. 애플리케이션 실행

PostgreSQL:

```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=postgres"
```

MySQL:

```powershell
cd backend
.\gradlew.bat bootRun --args="--spring.profiles.active=mysql"
```

별도 profile 없이 실행하면 인메모리 H2를 사용합니다.

## API 사용법

### 카페 검색

```http
GET /api/v1/search/cafes
```

주요 파라미터:

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `query` | O | 검색어 |
| `swLat`, `swLng`, `neLat`, `neLng` | X | 검색 범위를 제한할 지도 bounds |
| `lat`, `lng`, `radius` | X | 중심 좌표와 반경 조건 |
| `limit` | X | 기본 50, 최대 100 |
| `strategy` | X | 기본 `DB_LIKE` |

예시:

```powershell
curl "http://localhost:8080/api/v1/search/cafes?query=Latte&swLat=37.40&swLng=126.80&neLat=37.80&neLng=127.20&limit=100&strategy=DB_LIKE"
```

### 지도 마커 조회

```http
GET /api/v1/cafes/markers
```

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `swLat`, `swLng`, `neLat`, `neLng` | O | 지도 bounds |
| `zoom` | O | zoom 단계에 따른 결과 개수 제한 |
| `strategy` | X | 기본 `BATCH_METRIC_HOT_RANK` |

예시:

```powershell
curl "http://localhost:8080/api/v1/cafes/markers?swLat=37.40&swLng=126.80&neLat=37.80&neLng=127.20&zoom=13&strategy=DB_HOT_RANK_LIMIT"
```

## 테스트

일반 테스트:

```powershell
cd backend
.\gradlew.bat test
```

H2 기반 성능 비교 테스트:

```powershell
.\gradlew.bat performanceTest
```

PostgreSQL 1,000건 DB 성능 테스트:

```powershell
.\gradlew.bat dbPerformanceTest -DdbProfile=postgres -DperfCafeCount=1000
```

MySQL 1,000건 DB 성능 테스트:

```powershell
.\gradlew.bat dbPerformanceTest -DdbProfile=mysql -DperfCafeCount=1000
```

데이터 규모를 확장하려면 `perfCafeCount`를 `3000` 또는 `10000`으로 변경합니다. 고비용 N+1 비교 전략을 제외하려면 `-DperfIncludeExpensiveStrategies=false`를 추가합니다.

테스트 fixture는 반복 실험에서 동일한 후보 분포를 만들기 위해 카페 좌표를 서울 주변 범위의 인덱스 기반 grid로 생성합니다. 카페마다 메뉴 1~3개와 CafeMetric을 만들고 일부 대표 메뉴에는 `Latte` 키워드를 포함합니다.

## k6 부하 테스트

애플리케이션과 DB를 먼저 실행한 뒤 프로젝트 루트에서 다음 명령을 사용합니다.

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e VUS=10 -e DURATION=30s -e BOUNDS=wide docs/performance/search-and-marker.k6.js
```

특정 전략만 측정할 수도 있습니다.

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e SEARCH_STRATEGIES=DB_LIKE -e MARKER_STRATEGIES=DB_HOT_RANK_LIMIT docs/performance/search-and-marker.k6.js
```

## 대표 측정 결과

아래 값은 동일한 로컬 환경에서 수행한 상대 비교 결과입니다. 운영 환경의 절대 성능을 의미하지 않으며 하드웨어, DB cache, 백그라운드 프로세스 상태에 따라 달라질 수 있습니다.

### 검색 1,000건 p95

| DB | APP_CONTAINS | DB_LIKE | NORMALIZED |
| --- | ---: | ---: | ---: |
| PostgreSQL | 98 ms | **35 ms** | 54 ms |
| MySQL | 135 ms | **73 ms** | 81 ms |

세 전략의 `avgSearchScore`는 모두 58.31로 동일했습니다.

### 지도 마커 1,000건

| DB | 전략 | p95 | Query 수 | topHotCoverage |
| --- | --- | ---: | ---: | ---: |
| PostgreSQL | UNSORTED_LIMIT | 309 ms | 302 | 0.267 |
| PostgreSQL | HOT_RANK_LIMIT | 1362 ms | 1002 | 1.000 |
| PostgreSQL | BATCH_METRIC_HOT_RANK | 59 ms | 3 | 1.000 |
| PostgreSQL | DB_HOT_RANK_LIMIT | **18 ms** | **1** | 1.000 |
| MySQL | UNSORTED_LIMIT | 518 ms | 302 | 0.300 |
| MySQL | HOT_RANK_LIMIT | 1343 ms | 1002 | 1.000 |
| MySQL | BATCH_METRIC_HOT_RANK | 68 ms | 3 | 1.000 |
| MySQL | DB_HOT_RANK_LIMIT | **20 ms** | **1** | 1.000 |

`UNSORTED_LIMIT`은 정렬 비용을 줄이는 대신 HOT 상위 후보를 놓쳤습니다. `HOT_RANK_LIMIT`은 품질을 보존했지만 N+1 query로 latency가 급증했습니다. batch 조회와 DB projection 전략은 품질을 유지하면서 query count와 p95를 크게 줄였습니다.

### PostgreSQL 10,000건 확장

| 시나리오 | 전략 | p95 | Query 수 | 품질 지표 |
| --- | --- | ---: | ---: | --- |
| 검색 | APP_CONTAINS | 354 ms | 1 | avgScore 59.78 |
| 검색 | DB_LIKE | **81 ms** | 1 | avgScore 59.78 |
| 검색 | NORMALIZED | 284 ms | 1 | avgScore 59.78 |
| 마커 | BATCH_METRIC_HOT_RANK | 104 ms | 3 | TopHot 1.000 |
| 마커 | DB_HOT_RANK_LIMIT | **29 ms** | **1** | TopHot 1.000 |

## 결론

이 실험의 추천 조합은 `DB_LIKE + DB_HOT_RANK_LIMIT`입니다.

- 검색 조건을 DB에 pushdown해 Application으로 전달되는 후보 수를 줄였습니다.
- 지도 마커의 HOT 정렬과 limit을 DB에서 처리해 N+1 query를 제거했습니다.
- HOT 상위 결과 품질을 유지하면서 Application 메모리 사용량과 DB round-trip을 줄였습니다.

핵심은 단순히 어떤 정렬 알고리즘을 선택하는지가 아니라, **검색·결합·정렬·top-K 처리를 어느 레이어에서 수행하느냐**입니다.
