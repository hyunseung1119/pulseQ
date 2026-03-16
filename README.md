# PulseQ

> 선착순 이벤트를 안전하고 공정하게 처리하는 실시간 대기열 SaaS API

## What is PulseQ?

티켓팅, 수강신청, 한정판 세일 등 **순간 트래픽 폭주** 문제를 해결하는 대기열 엔진입니다.
API 하나로 가상 대기열 + 봇 탐지 + 실시간 모니터링을 제공합니다.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin 2.0, Spring Boot 3.4, WebFlux, Coroutines |
| Database | PostgreSQL 16, Redis 7 (Sorted Set + Distributed Lock) |
| Messaging | Apache Kafka (3 topics, event streaming) |
| ML | Python 3.12, FastAPI, LightGBM (bot detection) |
| Frontend | React 18, TypeScript, Vite, TanStack Query/Router, Tailwind v4, Recharts |
| Monitoring | Prometheus, Grafana |
| Infra | Docker Compose, Nginx |

## Architecture

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

## Project Structure (Full Tree)

```
pulseq/
├── docs/                                    # 기획/설계 산출물
│   ├── pm/PRD.md                           # Product Requirements Document
│   ├── backend/
│   │   ├── ARCHITECTURE.md                 # 아키텍처 결정 기록 (ADR)
│   │   └── API_SPEC.md                     # REST API 명세
│   ├── frontend/WIREFRAME.md               # UI 화면 설계서
│   ├── db/SCHEMA.md                        # DB 스키마 설계
│   ├── infra/INFRASTRUCTURE.md             # 인프라 구성/비용 산정
│   └── ml/ML_SPEC.md                       # ML 모델 명세
│
├── pulseq-api/                              # [모듈] API Layer — 요청/응답 처리
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/pulseq/api/
│       ├── PulseQApplication.kt            # Spring Boot 엔트리포인트
│       ├── config/
│       │   ├── GlobalExceptionHandler.kt   # RFC 9457 에러 응답
│       │   └── QueueScheduler.kt           # 대기열 자동 처리 스케줄러
│       ├── controller/
│       │   ├── TenantController.kt         # 회원가입/로그인/API키 관리
│       │   ├── EventController.kt          # 이벤트 CRUD
│       │   ├── QueueController.kt          # 대기열 입장/위치/처리/검증
│       │   ├── StatsController.kt          # 이벤트 통계 + 로그 조회
│       │   └── HealthController.kt         # 시스템 헬스체크
│       ├── dto/
│       │   ├── ApiResponse.kt              # 공통 응답 래퍼
│       │   ├── TenantDto.kt                # 테넌트 요청/응답 DTO
│       │   ├── EventDto.kt                 # 이벤트 요청/응답 DTO
│       │   └── QueueDto.kt                 # 대기열 요청/응답 DTO
│       ├── security/
│       │   ├── SecurityConfig.kt           # Spring Security 설정
│       │   ├── JwtProvider.kt              # JWT 토큰 생성/검증
│       │   └── JwtAuthenticationFilter.kt  # 인증 필터
│       └── resources/
│           ├── application.yml             # 메인 설정
│           └── application-test.yml        # 테스트 환경 설정
│
├── pulseq-core/                             # [모듈] Domain — 순수 비즈니스 로직
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/pulseq/core/
│       ├── domain/                         # 도메인 모델
│       │   ├── Tenant.kt                   # 테넌트 (Plan, Status)
│       │   ├── Event.kt                    # 이벤트 (EventStatus)
│       │   ├── QueueEntry.kt               # 대기열 엔트리 + EntryToken
│       │   └── QueueEventLog.kt            # 이벤트 로그 (QueueEventType enum)
│       ├── port/                           # 포트 (Hexagonal Architecture)
│       │   ├── TenantRepository.kt         # 테넌트 저장소 인터페이스
│       │   ├── EventRepository.kt          # 이벤트 저장소 인터페이스
│       │   ├── QueueEntryRepository.kt     # 대기열 엔트리 저장소
│       │   ├── QueuePort.kt                # Redis 대기열 포트
│       │   ├── QueueEventLogRepository.kt  # 이벤트 로그 저장소
│       │   ├── EventPublisher.kt           # 이벤트 발행 포트 (Kafka)
│       │   └── BotDetectionPort.kt         # 봇 탐지 포트 (ML)
│       ├── service/                        # 비즈니스 서비스
│       │   ├── TenantService.kt            # 회원가입, 인증, API키 발급
│       │   ├── EventService.kt             # 이벤트 CRUD + 검증
│       │   ├── QueueService.kt             # 대기열 핵심 로직 (입장/처리/검증)
│       │   ├── BotDetectionService.kt      # 봇 탐지 (캐시→ML→Rule fallback)
│       │   └── ApiKeyGenerator.kt          # API 키 생성 유틸
│       └── exception/
│           └── PulseQException.kt          # 커스텀 예외 (403 BotDetected 등)
│
├── pulseq-infra/                            # [모듈] Infrastructure — 외부 시스템 연동
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/pulseq/infra/
│       ├── config/
│       │   ├── JpaConfig.kt                # JPA 설정
│       │   └── RedissonConfig.kt           # Redisson 분산 락 설정
│       ├── persistence/                    # JPA 어댑터 (PostgreSQL)
│       │   ├── entity/
│       │   │   ├── TenantEntity.kt
│       │   │   ├── EventEntity.kt
│       │   │   ├── QueueEntryEntity.kt
│       │   │   └── QueueEventLogEntity.kt  # JSONB payload 컬럼
│       │   ├── repository/
│       │   │   ├── JpaTenantRepository.kt
│       │   │   ├── JpaEventRepository.kt
│       │   │   ├── JpaQueueEntryRepository.kt
│       │   │   └── JpaQueueEventLogRepository.kt
│       │   └── adapter/
│       │       ├── TenantRepositoryAdapter.kt
│       │       ├── EventRepositoryAdapter.kt
│       │       ├── QueueEntryRepositoryAdapter.kt
│       │       └── QueueEventLogAdapter.kt
│       ├── redis/
│       │   └── RedisQueueAdapter.kt        # Redis Sorted Set (ZADD/ZRANK/ZPOPMIN)
│       ├── kafka/
│       │   ├── KafkaConfig.kt              # 3 토픽, 프로듀서/컨슈머 설정
│       │   ├── KafkaQueueEventPublisher.kt # 이벤트 발행 어댑터
│       │   └── KafkaQueueEventConsumer.kt  # 이벤트 소비 → DB 저장
│       ├── ml/
│       │   └── MlBotDetectionAdapter.kt    # WebClient → ML 서비스 호출
│       └── resources/db/migration/
│           ├── V001__create_tenants_table.sql
│           ├── V002__create_events_table.sql
│           ├── V003__create_queue_entries_table.sql
│           └── V004__create_queue_event_log_table.sql  # BRIN 인덱스
│
├── frontend/                                # [모듈] React Dashboard
│   ├── Dockerfile                          # Nginx 기반 프로덕션 빌드
│   ├── nginx.conf                          # API/WS 리버스 프록시
│   ├── vite.config.ts                      # Vite + Tailwind + 프록시 설정
│   └── src/
│       ├── main.tsx                        # 엔트리 (QueryClient + RouterProvider)
│       ├── index.css                       # Tailwind v4 테마 정의
│       ├── app/
│       │   ├── router.tsx                  # TanStack Router 라우트 트리
│       │   ├── layout/
│       │   │   └── DashboardLayout.tsx     # 사이드바 + Outlet 레이아웃
│       │   └── routes/
│       │       ├── index.tsx               # 랜딩 페이지
│       │       ├── login.tsx               # 로그인
│       │       ├── signup.tsx              # 회원가입
│       │       ├── queue.$eventSlug.tsx    # 대기열 사용자 화면 (WebSocket)
│       │       └── dashboard/
│       │           ├── index.tsx           # 대시보드 메인 (KPI 카드)
│       │           ├── events.tsx          # 이벤트 목록 테이블
│       │           ├── events.new.tsx      # 이벤트 생성 폼
│       │           ├── events.$id.tsx      # 실시간 모니터링 (차트 + 봇 로그)
│       │           ├── events.$id.edit.tsx # 이벤트 수정
│       │           ├── api-keys.tsx        # API 키 관리 + 연동 가이드
│       │           ├── usage.tsx           # 사용량/과금 차트
│       │           └── settings.tsx        # 계정 정보
│       ├── features/
│       │   ├── auth/
│       │   │   ├── api.ts                  # 로그인/가입/프로필 API
│       │   │   └── store.ts               # Zustand 인증 상태
│       │   └── events/
│       │       ├── api.ts                  # 이벤트/큐/통계 API
│       │       └── EventForm.tsx           # 공유 이벤트 폼 컴포넌트
│       └── shared/
│           ├── components/ui/
│           │   ├── Button.tsx              # variant + size 조합
│           │   ├── Input.tsx
│           │   ├── Card.tsx
│           │   ├── Badge.tsx               # 상태별 색상 매핑
│           │   └── StatCard.tsx            # KPI 카드 (아이콘 + 트렌드)
│           ├── hooks/
│           │   ├── useWebSocket.ts         # WS + exponential backoff
│           │   └── useInterval.ts          # setInterval 래퍼
│           └── lib/
│               ├── api-client.ts           # Axios + JWT 인터셉터 + 401 처리
│               ├── cn.ts                   # clsx + tailwind-merge
│               ├── constants.ts            # AUTH_TOKEN_KEY, CHART_COLORS
│               └── format.ts              # 숫자/시간/퍼센트 포맷터
│
├── ml/                                      # [모듈] Python ML Service
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── app/
│   │   ├── main.py                         # FastAPI 앱 + 모델 로딩
│   │   ├── config.py                       # 모델 경로, 임계치 설정
│   │   ├── api/
│   │   │   ├── routes.py                   # POST /ml/bot-score, GET /ml/health
│   │   │   └── schemas.py                  # Pydantic 모델 (17개 피처)
│   │   └── models/
│   │       └── bot_detector.py             # LGBMClassifier 래퍼
│   └── training/
│       ├── generate_data.py                # 100K 샘플 시뮬레이션 (3 봇 유형)
│       └── train.py                        # LightGBM 학습 + 평가
│
├── scripts/                                 # 통합 테스트 스크립트
│   ├── test-phase3-kafka.sh                # Kafka 파이프라인 검증
│   └── test-phase4-bot.sh                  # 봇 탐지 E2E 검증
│
├── build.gradle.kts                         # 루트 빌드 (멀티모듈)
├── settings.gradle.kts                      # 모듈 선언
└── docker-compose.yml                       # Core + Pipeline + ML + Monitoring + Frontend
```

## Quick Start

```bash
# 1. 인프라 (PostgreSQL + Redis + Kafka + ML)
docker compose --profile pipeline up -d

# 2. ML 모델 학습 (최초 1회)
cd ml && python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python training/generate_data.py && python training/train.py
uvicorn app.main:app --port 8000 &

# 3. Spring Boot API
./gradlew :pulseq-api:bootRun

# 4. React Dashboard
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/tenants/signup` | 회원가입 |
| POST | `/api/v1/tenants/login` | 로그인 (JWT) |
| GET | `/api/v1/tenants/me` | 내 정보 |
| POST | `/api/v1/events` | 이벤트 생성 |
| GET | `/api/v1/events` | 이벤트 목록 |
| POST | `/api/v1/queues/{id}/enter` | 대기열 입장 |
| GET | `/api/v1/queues/{id}/status` | 큐 상태 조회 |
| POST | `/api/v1/queues/{id}/process` | 큐 처리 (입장 허가) |
| GET | `/api/v1/stats/{id}` | 이벤트 통계 |
| WS | `/ws/queues/{id}` | 실시간 위치 업데이트 |

## Development Phases

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Foundation (멀티모듈 + 테넌트/이벤트 API) | Done |
| 2 | Queue Engine (Redis Sorted Set + 입장 토큰) | Done |
| 3 | Kafka Pipeline (이벤트 스트리밍 + 통계) | Done |
| 4 | Bot Detection (LightGBM ML + Rule fallback) | Done |
| 5 | React Dashboard (실시간 모니터링 + CRUD) | Done |
| 6 | Observability + AWS (Prometheus/Grafana + 배포) | Next |

## Documentation

- [PRD (기획서)](docs/pm/PRD.md)
- [Architecture (아키텍처)](docs/backend/ARCHITECTURE.md)
- [API Specification (API 명세)](docs/backend/API_SPEC.md)
- [DB Schema (DB 설계)](docs/db/SCHEMA.md)
- [Frontend Wireframe (화면 설계)](docs/frontend/WIREFRAME.md)
- [Infrastructure (인프라/비용)](docs/infra/INFRASTRUCTURE.md)
- [ML Specification (ML 설계)](docs/ml/ML_SPEC.md)

## License

MIT
