# PulseQ — 기술 아키텍처 문서

## 1. 시스템 아키텍처 개요

```
                        ┌─────────────────┐
                        │   React Client   │
                        │  (Dashboard)     │
                        └────────┬────────┘
                                 │ HTTPS
                        ┌────────▼────────┐
                        │   Nginx / ALB    │
                        │  (Reverse Proxy) │
                        └────────┬────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                   │
     ┌────────▼────────┐ ┌──────▼───────┐  ┌───────▼────────┐
     │  Queue Service   │ │ Tenant Service│  │  Event Service  │
     │  (Kotlin/Spring) │ │ (Kotlin/Spring│  │ (Kotlin/Spring) │
     │                  │ │  Boot)        │  │                 │
     │  - 대기열 입장    │ │ - 회원가입     │  │ - Kafka Producer│
     │  - 순번 조회     │ │ - API 키 발급  │  │ - 통계 집계     │
     │  - 입장 허가     │ │ - 과금 관리    │  │                 │
     │  - 처리량 제어    │ │              │  │                 │
     └───────┬─────────┘ └──────┬───────┘  └───────┬─────────┘
             │                  │                    │
    ┌────────▼────────┐  ┌─────▼──────┐   ┌────────▼────────┐
    │     Redis        │  │ PostgreSQL │   │     Kafka       │
    │                  │  │            │   │                 │
    │ - Sorted Set     │  │ - tenants  │   │ - queue-events  │
    │   (대기열)        │  │ - events   │   │ - bot-events    │
    │ - 분산 락        │  │ - api_keys │   │ - metrics       │
    │ - Rate Limiter   │  │ - audit_log│   │                 │
    │ - 입장 토큰      │  │            │   │                 │
    └──────────────────┘  └────────────┘   └────────┬────────┘
                                                     │
                                            ┌────────▼────────┐
                                            │  ML Service     │
                                            │  (Python/FastAPI)│
                                            │                 │
                                            │  - 봇 탐지 모델  │
                                            │  - 트래픽 예측   │
                                            └─────────────────┘
```

## 2. 기술 스택 상세

### 백엔드
| 기술 | 버전 | 선택 근거 |
|------|------|----------|
| Kotlin | 2.0+ | Java 대비 간결한 문법, null safety, 코루틴 지원 |
| Spring Boot | 3.3+ | 업계 표준, 풍부한 생태계, WebFlux 지원 |
| Spring WebFlux | — | 비동기/논블로킹 I/O → 대기열 처리 성능 극대화 |
| Gradle (Kotlin DSL) | 8.x | 빌드 스크립트 타입 안전성 |

### 데이터
| 기술 | 용도 | 선택 근거 |
|------|------|----------|
| PostgreSQL 16 | 영구 데이터 (테넌트, 이벤트, 감사 로그) | ACID, JSON 지원, 무료 |
| Redis 7 | 대기열(Sorted Set), 분산 락, 캐시, Rate Limiting | sub-ms 응답, 원자적 연산 |
| Kafka 3.7 | 이벤트 스트리밍, 비동기 처리 | 높은 처리량, 영속성, 리플레이 |

### 프론트엔드
| 기술 | 선택 근거 |
|------|----------|
| React 18 + TypeScript | 업계 표준, 타입 안전성 |
| Vite | 빠른 빌드, HMR |
| TanStack Query | 서버 상태 관리 |
| Recharts | 실시간 차트 |
| Tailwind CSS | 유틸리티 기반 스타일링 |

### ML
| 기술 | 용도 |
|------|------|
| Python 3.12 + FastAPI | ML 모델 서빙 API |
| scikit-learn | 봇 탐지 이진 분류 (LightGBM) |
| pandas | 피처 엔지니어링 |

### 인프라
| 기술 | 용도 |
|------|------|
| Docker + Docker Compose | 로컬 개발 환경 (풀스택) |
| Prometheus | 메트릭 수집 |
| Grafana | 메트릭 시각화 |
| AWS (프리티어) | 프로덕션 배포 (Phase 6) |

## 3. 아키텍처 결정 기록 (ADR)

### ADR-001: 모놀리식 vs MSA

**결정**: 모듈러 모놀리스 (Modular Monolith)

**근거**:
- 사이드 프로젝트 1인 개발 → MSA는 운영 오버헤드 과다
- Spring Boot 멀티 모듈로 도메인 분리 → 추후 MSA 전환 용이
- 배포/디버깅 단순화

**구조**:
```
pulseq/
├── pulseq-api          # REST API 진입점 (Controller)
├── pulseq-core         # 핵심 도메인 로직 (대기열, 이벤트)
├── pulseq-infra        # Redis, Kafka, DB 연동
└── pulseq-ml-client    # ML 서비스 호출 클라이언트
```

### ADR-002: WebFlux vs MVC

**결정**: Spring WebFlux (Reactive)

**근거**:
- 대기열 시스템은 I/O bound (Redis, Kafka, DB)
- 논블로킹 I/O로 스레드 효율성 극대화
- 동시 접속 10,000+ 처리 시 스레드 풀 고갈 방지
- Kotlin 코루틴과 자연스럽게 결합
- 벤치마크: MVC 대비 동시 접속 처리량 3-5배 향상 (동일 리소스)

**트레이드오프**:
- 학습 곡선 높음 (Mono/Flux, 코루틴)
- 디버깅 복잡도 증가
- → 주니어 학습 관점에서 오히려 장점 (리액티브 프로그래밍 경험)

### ADR-003: 대기열 자료구조

**결정**: Redis Sorted Set

**근거**:
- `ZADD` O(log N) — 입장 순서대로 score(timestamp) 부여
- `ZRANK` O(log N) — 현재 내 순번 즉시 조회
- `ZPOPMIN` O(log N) — 다음 처리 대상 추출
- 원자적 연산 → 순번 중복 불가능
- 대안 검토:
  - Redis List: 순번 조회 O(N) → 대규모에서 성능 저하
  - RabbitMQ: 순번 조회 불가, 대기열 현황 파악 어려움
  - DB Queue: 레이턴시 10-100x 높음

### ADR-004: 분산 락 구현

**결정**: Redisson (Redis 기반 분산 락)

**근거**:
- Redis 단일 인스턴스에서도 안전한 락 제공
- Watchdog 자동 연장 → 데드락 방지
- 대안 검토:
  - `SETNX` 직접 구현: 타임아웃/갱신 관리 복잡
  - ZooKeeper: 추가 인프라 부담
  - DB Lock: 레이턴시 과다

### ADR-005: 봇 탐지 아키텍처

**결정**: 비동기 스코어링 + 임계치 차단

**근거**:
- 실시간 요청 경로에 ML 추론 넣으면 레이턴시 증가
- → Kafka로 행동 데이터 비동기 수집 → ML 서비스에서 스코어링
- → 스코어 결과를 Redis에 캐싱 → 다음 요청 시 Redis에서 조회
- 차단 판단은 Redis 조회만 (sub-ms)

```
요청 → Redis 봇 스코어 확인 (캐시 히트) → 통과/차단
         ↓ (캐시 미스)
     Kafka → ML Service → 스코어 계산 → Redis 저장
```

## 4. 로컬 개발 환경 (PC 성능 고려)

### 최소 사양
| 리소스 | 요구량 | 비고 |
|--------|--------|------|
| RAM | 8GB+ | Docker 컨테이너 전체 약 4GB 사용 |
| CPU | 4코어+ | Kafka + Redis + PostgreSQL 동시 실행 |
| Disk | 10GB+ | Docker 이미지 + 데이터 |

### Docker Compose 리소스 제한
```yaml
# 각 서비스별 메모리 제한 (총 ~4GB)
postgres:    512MB
redis:       256MB
kafka:       1GB (+ zookeeper 512MB)
app:         512MB
ml-service:  512MB
prometheus:  256MB
grafana:     256MB
```

### 테스트 데이터 규모 (PC 범위 내)
| 데이터 | 로컬 규모 | 실무 환산 |
|--------|----------|----------|
| 테넌트 | 10개 | 수백 개 |
| 이벤트 | 100개 | 수만 개 |
| 대기열 사용자 | 10,000명 | 수십만 명 |
| 이벤트 로그 | 100만 건 | 수억 건 |
| 봇 탐지 학습 데이터 | 10만 건 | 수천만 건 |

## 5. 보안 아키텍처

| 레이어 | 구현 |
|--------|------|
| 인증 | API 키 (X-API-Key 헤더) + JWT (대시보드) |
| 인가 | 테넌트 격리 — 모든 쿼리에 tenant_id 조건 필수 |
| Rate Limiting | Redis Sliding Window — 테넌트별 플랜에 따른 제한 |
| 입력 검증 | Jakarta Validation + 커스텀 Validator |
| 시크릿 관리 | 환경변수 (.env) — 코드에 절대 하드코딩 금지 |
| CORS | 허용 도메인 화이트리스트 |
| SQL Injection | JPA Parameterized Query 전용 |

## 6. 에러 처리 전략

```kotlin
// RFC 9457 Problem Details 형식
{
    "type": "https://pulseq.io/errors/queue-full",
    "title": "Queue Capacity Exceeded",
    "status": 429,
    "detail": "Event 'concert-2026' has reached maximum capacity of 50000",
    "instance": "/api/v1/queues/concert-2026/enter",
    "extensions": {
        "eventId": "concert-2026",
        "currentSize": 50000,
        "maxSize": 50000
    }
}
```
