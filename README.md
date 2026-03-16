# PulseQ

> 선착순 이벤트를 안전하고 공정하게 처리하는 실시간 대기열 SaaS API

## What is PulseQ?

티켓팅, 수강신청, 한정판 세일 등 **순간 트래픽 폭주** 문제를 해결하는 대기열 엔진입니다.
API 하나로 가상 대기열 + 봇 탐지 + 실시간 모니터링을 제공합니다.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Kotlin, Spring Boot 3, WebFlux, Coroutines |
| Database | PostgreSQL 16, Redis 7 |
| Messaging | Apache Kafka |
| ML | Python, FastAPI, LightGBM |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS |
| Monitoring | Prometheus, Grafana |
| Infra | Docker Compose, AWS (EC2, RDS, S3) |

## Architecture

```
Client → Nginx → Spring Boot API → Redis (Queue + Cache)
                                  → PostgreSQL (Persistent)
                                  → Kafka (Event Streaming)
                                  → ML Service (Bot Detection)
```

## Project Structure

```
pulseq/
├── docs/                    # 기획/설계 문서
│   ├── pm/PRD.md           # Product Requirements Document
│   ├── backend/            # 아키텍처, API 명세
│   ├── frontend/           # 화면 설계
│   ├── db/                 # DB 스키마
│   ├── infra/              # 인프라 구성, 비용 산정
│   └── ml/                 # ML 모델 명세
├── pulseq-api/             # REST API (Controller)
├── pulseq-core/            # Domain Logic
├── pulseq-infra/           # Redis, Kafka, DB 연동
├── frontend/               # React Dashboard
├── ml/                     # Python ML Service
└── docker-compose.yml
```

## Getting Started

```bash
# 로컬 실행 (Docker 필요)
docker compose up -d

# API 서버
./gradlew :pulseq-api:bootRun

# 프론트엔드
cd frontend && npm run dev
```

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
