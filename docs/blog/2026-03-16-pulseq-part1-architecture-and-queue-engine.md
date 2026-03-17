---
title: "PulseQ 개발기 Part 1: 선착순 대기열 SaaS를 설계하고 구현하기까지"
date: 2026-03-16
updated: 2026-03-16
tags: [Kotlin, Spring Boot, Redis, Kafka, WebFlux, Distributed Lock, Queue System, SaaS]
category: retrospective
description: "티켓팅/수강신청의 트래픽 폭주를 해결하는 대기열 SaaS API를 Kotlin + Spring Boot + Redis + Kafka로 설계하고 구현한 과정. 분산 락 경합 버그를 디버깅하고 처리량을 506% 개선한 실전 기록."
toc: true
---

# PulseQ 개발기 Part 1: 선착순 대기열 SaaS를 설계하고 구현하기까지

> **TL;DR**: 티켓팅/수강신청 같은 선착순 이벤트에서 트래픽 폭주를 해결하는 대기열 SaaS를 1주일 만에 설계부터 구현까지 완성했다. Redis Sorted Set 기반 대기열 + LightGBM 봇 탐지 + Kafka 이벤트 스트리밍 + React 실시간 대시보드까지. 분산 락 경합 버그를 만나 처리량이 10.9 req/s까지 떨어졌다가, 락 범위를 이벤트 단위에서 유저 단위로 줄여 66.0 req/s로 **506% 개선**한 과정을 기록한다.

## 왜 이 프로젝트를 만들었나

수강신청 시스템을 생각해보자. 개강 직전 수만 명이 동시에 접속해서 같은 버튼을 누른다. 서버는 터지고, 빠른 클릭 매크로를 쓴 사람이 이기고, 나머지는 화면만 새로고침한다.

이 문제를 풀려면 세 가지가 필요하다:

1. **가상 대기열** — 요청을 줄 세우고 순서대로 처리
2. **봇 탐지** — 매크로/자동화 도구 차단
3. **실시간 모니터링** — 운영자가 상황을 파악하고 개입할 수 있는 대시보드

PulseQ는 이 세 가지를 **API 하나로** 제공하는 SaaS를 목표로 설계했다.

## 아키텍처 한눈에 보기

```
                    ┌─────────────────────────────────────────┐
                    │              React Dashboard             │
                    │         (Vite + TanStack + Recharts)     │
                    └──────────────┬──────────────────────────┘
                                   │ HTTP / WebSocket
                    ┌──────────────▼──────────────────────────┐
                    │         Spring Boot API (WebFlux)        │
                    │    JWT Auth │ Rate Limit │ RFC 9457      │
                    └──┬─────────┬─────────┬─────────┬────────┘
                       │         │         │         │
              ┌────────▼──┐  ┌──▼──────┐  │  ┌──────▼──────┐
              │ PostgreSQL │  │  Redis  │  │  │  ML Service  │
              │   (JPA +   │  │ Sorted  │  │  │  (FastAPI +  │
              │  Flyway)   │  │  Set +  │  │  │  LightGBM)  │
              │            │  │  Lock)  │  │  │             │
              └────────────┘  └─────────┘  │  └─────────────┘
                                           │
                                    ┌──────▼──────┐
                                    │    Kafka    │
                                    │  (3 topics  │
                                    │  6 partitions)│
                                    └─────────────┘
```

## 프로젝트 구조

멀티모듈 Hexagonal Architecture를 적용했다. "왜 헥사고날인가?" — 이 프로젝트에서는 Redis, PostgreSQL, Kafka, ML 서비스 등 외부 시스템이 4개나 된다. 비즈니스 로직이 특정 인프라에 의존하면 테스트와 교체가 어렵다.

```
pulseq/
├── pulseq-api/          ← 요청/응답 처리 (Controller, Security, DTO)
│   └── src/main/kotlin/com/pulseq/api/
│       ├── controller/
│       │   ├── TenantController.kt     # 회원가입/로그인/API키
│       │   ├── EventController.kt      # 이벤트 CRUD
│       │   ├── QueueController.kt      # 대기열 입장/위치/처리
│       │   └── StatsController.kt      # 통계 + 로그 조회
│       ├── security/
│       │   ├── SecurityConfig.kt       # Spring Security 설정
│       │   ├── JwtProvider.kt          # JWT 생성/검증
│       │   └── JwtAuthenticationFilter.kt
│       └── dto/
│           ├── ApiResponse.kt          # 공통 응답 래퍼
│           ├── TenantDto.kt
│           ├── EventDto.kt
│           └── QueueDto.kt
│
├── pulseq-core/         ← 순수 비즈니스 로직 (도메인, 포트, 서비스)
│   └── src/main/kotlin/com/pulseq/core/
│       ├── domain/
│       │   ├── Event.kt                # 이벤트 (상태: SCHEDULED→ACTIVE→COMPLETED)
│       │   ├── QueueEntry.kt           # 대기열 엔트리 + EntryToken
│       │   └── QueueEventLog.kt        # Kafka 이벤트 로그
│       ├── port/                       # Hexagonal 포트 (인터페이스)
│       │   ├── QueuePort.kt            # Redis 대기열 추상화
│       │   ├── EventPublisher.kt       # Kafka 발행 추상화
│       │   └── BotDetectionPort.kt     # ML 봇 탐지 추상화
│       └── service/
│           ├── QueueService.kt         # 핵심: 입장/처리/검증 로직
│           └── BotDetectionService.kt  # 캐시→ML→Rule fallback
│
├── pulseq-infra/        ← 외부 시스템 연동 (어댑터)
│   └── src/main/kotlin/com/pulseq/infra/
│       ├── redis/
│       │   └── RedisQueueAdapter.kt    # ZADD/ZRANK/ZPOPMIN + Redisson Lock
│       ├── kafka/
│       │   ├── KafkaQueueEventPublisher.kt
│       │   └── KafkaQueueEventConsumer.kt
│       ├── ml/
│       │   └── MlBotDetectionAdapter.kt # WebClient → FastAPI
│       └── persistence/
│           ├── entity/                 # JPA 엔티티
│           ├── repository/             # Spring Data JPA
│           └── adapter/               # 포트 구현체
│
├── frontend/            ← React 실시간 대시보드
│   └── src/
│       ├── app/routes/dashboard/       # 이벤트 관리, 실시간 모니터링
│       ├── features/events/api.ts      # API 클라이언트
│       └── shared/hooks/useWebSocket.ts # WS + 지수 백오프 재접속
│
├── ml/                  ← Python ML 봇 탐지 서비스
│   └── app/
│       ├── main.py                     # FastAPI + LightGBM
│       └── models/bot_detector.py      # 17개 피처 기반 분류기
│
└── scripts/
    └── load-test.sh                    # 부하 테스트 (curl + xargs)
```

**핵심 설계 결정**: `pulseq-core`는 Spring, Redis, Kafka에 대한 import가 **하나도 없다**. 모든 외부 의존성은 `port/` 인터페이스로 추상화되어 있고, `pulseq-infra`에서 구현한다.

## Phase별 구현 과정

### Phase 1: 멀티모듈 기반 + 테넌트/이벤트 API

멀티테넌트 SaaS이므로 첫 번째로 테넌트(고객사) 인증 시스템을 구축했다.

```kotlin
// JWT 기반 인증 플로우
POST /api/v1/tenants/signup  → 회원가입 (BCrypt 암호화)
POST /api/v1/tenants/login   → JWT 발급 (Access + Refresh)
GET  /api/v1/tenants/me      → 내 정보 조회
POST /api/v1/events          → 이벤트 생성 (대기열 설정 포함)
```

에러 응답은 RFC 9457 (Problem Details) 표준을 따른다:

```json
{
  "type": "https://pulseq.io/errors/event-not-found",
  "title": "Event Not Found",
  "status": 404,
  "detail": "Event with ID abc-123 does not exist",
  "instance": "/api/v1/events/abc-123"
}
```

### Phase 2: Redis Sorted Set 기반 대기열 엔진

대기열의 핵심은 "순서 보장"이다. Redis의 Sorted Set은 이 용도에 최적화되어 있다.

```
ZADD queue:{eventId} {timestamp} {userId}    → O(log N) 입장
ZRANK queue:{eventId} {userId}               → O(log N) 위치 조회
ZPOPMIN queue:{eventId} {batchSize}          → O(log N) 상위 N명 추출
```

**왜 Redis Sorted Set인가?**

| 대안 | 문제점 |
|------|--------|
| PostgreSQL 큐 테이블 | 순서 조회마다 ORDER BY → 인덱스 스캔, 동시성 ↓ |
| RabbitMQ | 메시지 소비 후 재조회 불가, "내 순번" 확인 안 됨 |
| Kafka | 순서 보장되지만 random access 불가 |
| Redis List (LPUSH/RPOP) | 중간 위치 조회 O(N), 중복 체크 불가 |
| **Redis Sorted Set** | **O(log N) 입장/조회, 자동 정렬, 원자적 ZADD** |

입장 토큰 시스템도 구현했다. 대기 후 순번이 오면 임시 토큰을 발급하고, 일정 시간 내에 사용하지 않으면 만료시킨다:

```kotlin
suspend fun processQueue(eventId: UUID): List<EntryToken> {
    val userIds = queuePort.dequeueTop(eventId, batchSize)  // ZPOPMIN
    return userIds.map { userId ->
        val token = generateEntryToken()
        queuePort.saveEntryToken(entryToken, ttlSeconds)    // Redis SET + TTL
        entryToken
    }
}
```

### Phase 3: Kafka 이벤트 스트리밍

모든 대기열 이벤트(입장, 퇴장, 봇 차단 등)를 Kafka로 스트리밍한다.

```
3개 토픽:
  queue-events       → 입장/퇴장/처리 이벤트 (6 파티션)
  queue-events-batch → 배치 입장 허가 이벤트
  queue-events-dlq   → 처리 실패 이벤트 (Dead Letter Queue)
```

**왜 Kafka인가?** — 대기열 이벤트는 순서가 중요하고, 이벤트별로 파티셔닝하면 같은 이벤트의 로그가 순서대로 처리된다. RabbitMQ도 가능하지만, Kafka는 이벤트 리플레이가 가능해서 통계 재계산에 유리하다.

### Phase 4: LightGBM 봇 탐지

봇 탐지는 3단계 fallback 전략을 사용한다:

```
1. Redis 캐시 확인 (같은 유저의 최근 판정 결과)
   ↓ miss
2. ML 서비스 호출 (FastAPI + LightGBM, 17개 피처)
   ↓ timeout/error
3. Rule 기반 fallback (IP 빈도, User-Agent 패턴)
```

ML 모델의 17개 피처 중 핵심:

```python
features = {
    "request_interval_mean": 0.5,    # 요청 간격 평균 (초)
    "request_interval_std": 0.01,    # 요청 간격 표준편차 (봇은 일정)
    "mouse_movement_count": 0,       # 마우스 이동 횟수
    "typing_speed_cpm": 0,           # 타이핑 속도
    "ip_request_count_1h": 500,      # 1시간 내 같은 IP 요청 수
    "user_agent_is_headless": True,  # Headless 브라우저 여부
    # ... 11개 더
}
```

### Phase 5: React 실시간 대시보드

React 18 + TypeScript + Vite + TanStack Query/Router + Tailwind CSS v4 + Recharts로 대시보드를 구축했다.

```
frontend/src/
├── app/
│   ├── router.tsx              # TanStack Router (auth guard 포함)
│   └── routes/
│       ├── dashboard/
│       │   ├── index.tsx       # KPI 카드 (입장/처리/봇차단 실시간 수치)
│       │   ├── events.tsx      # 이벤트 목록 테이블
│       │   └── events.$id.tsx  # 실시간 차트 (5초 자동갱신)
│       └── queue.$eventSlug.tsx # 대기열 사용자 화면 (WebSocket)
├── features/
│   ├── auth/store.ts           # Zustand 인증 상태
│   └── events/api.ts           # Axios + JWT 인터셉터
└── shared/
    └── hooks/useWebSocket.ts   # 지수 백오프 재접속
```

## 삽질 기록: 만난 에러들과 해결 과정

### 1. Tailwind CSS v4 설치 실패

**증상**: `npm install -D tailwindcss@4 @tailwindcss/vite` 실행 시 peer dependency 충돌
```
npm error ERESOLVE unable to resolve dependency tree
npm error peer tailwindcss@">=3.0.0 || insiders || >=4.0.0-alpha.20"
```

**원인**: React 18 + Tailwind v4 + Vite 플러그인 간 peer dependency 버전 불일치. Tailwind v4는 2025년 출시된 메이저 버전으로, 일부 플러그인이 아직 호환성 선언을 업데이트하지 않았다.

**해결**: `--legacy-peer-deps` 플래그로 설치. peer dependency 체크를 우회하되, 실제 런타임 호환성은 정상.

**교훈**: 새 메이저 버전 출시 초기에는 ecosystem이 따라오는 데 시간이 걸린다. `--legacy-peer-deps`는 "알고 쓰면" 합리적인 우회 수단이다.

### 2. `react-is` 모듈 누락

**증상**: `npm run build` 시 에러
```
[vite]: Rollup failed to resolve import "react-is"
```

**원인**: Recharts가 `react-is`를 peer dependency로 요구하지만, npm이 자동 설치하지 않은 경우. `--legacy-peer-deps`로 설치하면 peer dependency 자동 해결이 비활성화된다.

**해결**: `npm install react-is --legacy-peer-deps`로 명시적 설치.

**교훈**: `--legacy-peer-deps`를 쓰면 peer dependency를 수동으로 관리해야 한다. 빌드 에러가 나면 "이 패키지가 뭘 요구하는지" `npm ls`로 확인하자.

### 3. `tenant.usage` undefined 런타임 에러

**증상**: 대시보드 접속 시 화면 크래시
```
undefined is not an object (evaluating 'tenant?.usage.currentMonth')
```

**원인**: 백엔드 DTO에서 `usage` 필드가 nullable인데, 프론트엔드 TypeScript 타입에서 required로 선언했다.

```typescript
// Before (문제)
interface TenantInfo {
  usage: { currentMonth: number; limit: number }  // ← 항상 존재한다고 가정
}

// After (수정)
interface TenantInfo {
  usage?: { currentMonth: number; limit: number } | null  // ← nullable
}
```

3개 대시보드 페이지에서 `tenant.usage.currentMonth` → `tenant?.usage?.currentMonth ?? 0`으로 수정.

**교훈**: 백엔드 API의 응답 스키마와 프론트엔드 타입이 1:1 매핑되어야 한다. "백엔드가 항상 줄 거야"라는 가정은 위험하다. Optional chaining(`?.`)은 방어적으로 쓰자.

### 4. `events.filter is not a function` 런타임 에러

**증상**: 이벤트 목록 페이지에서 크래시
```
events.filter is not a function
```

**원인**: 백엔드가 페이지네이션 응답을 반환하는데 프론트엔드가 배열을 기대했다.

```json
// 프론트엔드 기대: { "data": [...] }
// 실제 응답:      { "data": { "content": [...], "totalPages": 1, ... } }
```

**해결**:
```typescript
export async function listEvents(status?: string) {
  const res = await apiClient.get<{
    success: boolean
    data: { content: EventResponse[] } | EventResponse[]
  }>('/events', { params })
  const data = res.data.data
  return Array.isArray(data) ? data : data.content  // 두 형태 모두 처리
}
```

**교훈**: API 응답 형태는 "실제로 호출해서 확인"해야 한다. Swagger 문서만 보고 타입을 정의하면 실제 응답과 다를 수 있다. 특히 Spring Data의 Page 객체는 content/totalPages/size 등을 감싸서 반환한다.

### 5. 분산 락 경합 — 가장 치명적인 버그

**증상**: 부하 테스트에서 POST /queues/{id}/enter가 80% 500 에러

```
Test 1 (POST enter): 10.9 req/s, 500 에러 80.2%
에러 로그: "Failed to acquire lock: queue:enter:{eventId}"
```

**초기 가설**: "서버 성능이 부족하다" → ❌ GET /queues/status는 118 req/s로 정상

**근본 원인 (Root Cause)**:

```kotlin
// QueueService.kt 80행 — 문제의 코드
return queuePort.withLock("queue:enter:$eventId") {  // ← 이벤트 전체에 하나의 락
    val enqueued = queuePort.enqueue(eventId, userId, score)
    // ... DB 저장, 카운터 업데이트, Kafka 발행 (각각 수~수십ms)
}
```

**분석**: `withLock("queue:enter:$eventId")` — 같은 이벤트에 입장하는 **모든 사용자**가 하나의 분산 락을 놓고 경쟁한다. Redisson 락의 waitMs가 5초인데, 락 내부 작업이 ~50ms 걸리면 5초 안에 처리할 수 있는 요청은 약 100개. 동시 50개 요청이 들어오면 대부분이 5초 timeout에 걸려 실패한다.

```
Timeline (50 concurrent requests to same event):

Request 1:  [====LOCK ACQUIRED====]........release
Request 2:  [waiting...][====LOCK====]........release
Request 3:  [waiting.........][====LOCK====]........release
...
Request 40: [waiting.............................5s TIMEOUT] → 500 ERROR
Request 50: [waiting.............................5s TIMEOUT] → 500 ERROR
```

**핵심 질문: "왜 이벤트 단위 락을 걸었는가?"**

처음에는 "같은 이벤트에 동시 입장하면 정원 초과 race condition이 발생할 수 있다"고 생각했다. 하지만 실제로는:

1. Redis `ZADD`는 **원자적**이다 — 중복 멤버 자동 방지
2. 정원 체크는 락 **바깥**에서 이미 하고 있었다 (line 74-77)
3. 락이 필요한 이유는 "같은 유저의 동시 입장 방지"뿐이다

**해결**:

```kotlin
// Before: 이벤트 단위 락 (모든 유저가 경쟁)
return queuePort.withLock("queue:enter:$eventId") { ... }

// After: 유저 단위 락 (같은 유저만 직렬화, 다른 유저는 병렬)
return queuePort.withLock("queue:enter:$eventId:$userId") { ... }
```

**결과**:

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| 처리량 | 10.9 req/s | **66.0 req/s** | **+506%** |
| 성공률 | 19.8% | **100%** | 500 에러 완전 제거 |
| p50 응답시간 | 5,060ms | **587ms** | **-88%** |
| p99 응답시간 | 5,095ms | **1,330ms** | **-74%** |

**교훈**: 분산 락의 범위(granularity)는 **최소한으로** 잡아야 한다. "안전하게 넓게 잡자"는 생각이 처리량을 1/6로 떨어뜨렸다. 락을 걸기 전에 "정말 이 범위 전체를 직렬화해야 하는가?"를 반드시 자문하자.

## 부하 테스트 최종 결과

테스트 환경: MacBook, Docker Compose (PostgreSQL 16 + Redis 7 + Kafka), 단일 Spring Boot 인스턴스

### Test 1: 대기열 입장 (POST /queues/{id}/enter)
```
500 requests, 50 concurrent
Throughput: 66.0 req/s
Success:    100% (201 Created)
Latency:    avg=640ms, p50=587ms, p90=921ms, p95=1,137ms, p99=1,330ms
```

### Test 2: 큐 상태 조회 (GET /queues/{id}/status)
```
500 requests, 50 concurrent
Throughput: 108.5 req/s
Success:    100% (200 OK)
Latency:    avg=112ms, p50=102ms, p90=159ms, p95=206ms, p99=450ms
```

### Test 3: 봇 탐지 포함 입장
```
100 requests, 20 concurrent
Throughput: 76.9 req/s
Latency:    avg=191ms, p50=171ms, p95=348ms, p99=478ms
```

봇 탐지가 켜져 있어도 76.9 req/s — ML 서비스 호출 오버헤드가 캐시 히트 시 거의 없다.

## 트레이드오프 기록

### 1. WebFlux vs MVC

| | WebFlux | MVC |
|--|---------|-----|
| 동시 연결 | 높음 (non-blocking) | Thread pool 한계 |
| 학습 곡선 | 높음 (코루틴, 리액티브) | 낮음 |
| 디버깅 | 어려움 (스택 트레이스 복잡) | 쉬움 |
| 선택 이유 | 대기열 = I/O 바운드 → **논블로킹 필수** | |

### 2. Sorted Set vs List (Redis)

Sorted Set을 선택했지만, 정렬 비용이 O(log N)이다. 단순 FIFO라면 List가 O(1)으로 더 빠르다. 하지만 "내 순번 조회" 기능을 위해 ZRANK가 필요했고, 이것은 List로는 O(N)이다.

### 3. 유저 단위 락 vs 락 제거

유저 단위 락 대신 락을 완전히 제거하는 방법도 있다. ZADD가 원자적이니까. 하지만 DB 저장 + 카운터 업데이트 + Kafka 발행이 한 묶음이어야 하는 요건이 있어서, 같은 유저의 동시 요청은 직렬화하기로 했다. 완전 제거하면 같은 유저가 두 번 누를 때 race condition이 생길 수 있다.

## 정리

1. **분산 락의 범위는 최소한으로** — 이벤트 단위 → 유저 단위로 바꾸니 506% 개선
2. **Redis Sorted Set은 대기열에 최적** — O(log N) 입장/조회, 원자적 ZADD
3. **API 응답 타입은 실제 호출로 검증** — Swagger 문서와 실제 응답이 다를 수 있다
4. **Optional chaining은 방어적으로** — 백엔드가 "항상" 보낸다는 가정 금물

## 다음 편 예고

**Part 2**: WebSocket 실시간 통신 + 봇 탐지 ML 파이프라인 심화
- WebSocket 지수 백오프 재접속 구현
- LightGBM 17개 피처 설계 근거
- 캐시 → ML → Rule 3단계 fallback 전략
- Prometheus + Grafana 모니터링 구축

## References

- [Redis Sorted Set 공식 문서](https://redis.io/docs/data-types/sorted-sets/)
- [Redisson Distributed Lock](https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers)
- [Spring WebFlux + Kotlin Coroutines](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
- [RFC 9457 - Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)

---
*이 글에서 사용한 환경: Kotlin 2.0, Spring Boot 3.4, Redis 7, PostgreSQL 16, Kafka, React 18, TypeScript, Vite*
*작성일: 2026-03-16*
