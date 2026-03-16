# PulseQ — 인프라 구성도 + 비용 산정

## 1. 로컬 개발 환경 (Docker Compose)

```
┌─────────────────────────── Docker Network: pulseq-net ───────────────────────────┐
│                                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │
│  │  pulseq-app  │  │  pulseq-ml   │  │ pulseq-front │  │     nginx          │    │
│  │  Kotlin/     │  │  Python/     │  │  React/      │  │  Reverse Proxy     │    │
│  │  Spring Boot │  │  FastAPI     │  │  Vite Dev    │  │  :80 → app:8080   │    │
│  │  :8080       │  │  :8000       │  │  :5173       │  │  :80/ml → ml:8000 │    │
│  │  512MB       │  │  512MB       │  │  256MB       │  │  128MB             │    │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘  └────────────────────┘    │
│         │                  │                                                      │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌────────────────────────────────────┐      │
│  │  PostgreSQL  │  │    Redis     │  │           Kafka                    │      │
│  │  :5432       │  │    :6379     │  │  ┌──────────┐  ┌───────────────┐  │      │
│  │  512MB       │  │    256MB     │  │  │ Zookeeper│  │ Kafka Broker  │  │      │
│  │              │  │              │  │  │ :2181    │  │ :9092         │  │      │
│  │  Volume:     │  │  Volume:     │  │  │ 256MB   │  │ 1GB           │  │      │
│  │  pgdata      │  │  redisdata   │  │  └──────────┘  └───────────────┘  │      │
│  └──────────────┘  └──────────────┘  └────────────────────────────────────┘      │
│                                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                            │
│  │  Prometheus  │  │   Grafana    │  │  Kafka UI    │                            │
│  │  :9090       │  │   :3000      │  │  :8989       │                            │
│  │  256MB       │  │   256MB      │  │  256MB       │                            │
│  └──────────────┘  └──────────────┘  └──────────────┘                            │
│                                                                                   │
│  총 메모리: ~4.2GB (8GB RAM PC에서 안전하게 실행 가능)                              │
└───────────────────────────────────────────────────────────────────────────────────┘
```

### Docker Compose 리소스 제한 요약

| 서비스 | 포트 | 메모리 제한 | CPU 제한 |
|--------|------|-----------|---------|
| pulseq-app | 8080 | 512MB | 1.0 |
| pulseq-ml | 8000 | 512MB | 0.5 |
| pulseq-front | 5173 | 256MB | 0.5 |
| postgres | 5432 | 512MB | 0.5 |
| redis | 6379 | 256MB | 0.25 |
| zookeeper | 2181 | 256MB | 0.25 |
| kafka | 9092 | 1GB | 1.0 |
| prometheus | 9090 | 256MB | 0.25 |
| grafana | 3000 | 256MB | 0.25 |
| nginx | 80 | 128MB | 0.25 |
| kafka-ui | 8989 | 256MB | 0.25 |
| **합계** | — | **~4.2GB** | **~5.0** |

### PC 성능별 가이드

| PC 사양 | 실행 가능 구성 |
|---------|---------------|
| 8GB RAM, 4코어 | 풀스택 실행 가능 (kafka-ui, grafana 선택적) |
| 16GB RAM, 4코어+ | 모든 서비스 + 부하 테스트 동시 실행 가능 |
| 8GB 미만 | Kafka/Prometheus 제외하고 핵심만 실행 |

### 최소 실행 프로파일

```bash
# 핵심만 (4GB RAM에서도 실행 가능)
docker compose --profile core up
# → app + postgres + redis + nginx

# 이벤트 파이프라인 추가
docker compose --profile pipeline up
# → core + kafka + zookeeper

# 풀스택
docker compose up
# → 전체 서비스
```

## 2. AWS 프로덕션 환경 (프리티어 최적화)

```
┌──────────────────────── AWS ap-northeast-2 (서울) ─────────────────────────┐
│                                                                            │
│  ┌─────────────┐                                                           │
│  │ Route 53    │  pulseq.io → CloudFront → ALB                            │
│  └──────┬──────┘                                                           │
│         │                                                                  │
│  ┌──────▼──────┐                                                           │
│  │ CloudFront  │  React SPA (S3) + API 캐싱                               │
│  └──────┬──────┘                                                           │
│         │                                                                  │
│  ┌──────▼──────┐     ┌─────────────────────────────────────┐              │
│  │    ALB      │────▶│  ECS Fargate                        │              │
│  │ (Free Tier) │     │  ┌───────────┐  ┌───────────────┐  │              │
│  └─────────────┘     │  │ pulseq-app│  │  pulseq-ml    │  │              │
│                      │  │ 0.25 vCPU │  │  0.25 vCPU    │  │              │
│                      │  │ 512MB     │  │  512MB        │  │              │
│                      │  └─────┬─────┘  └───────┬───────┘  │              │
│                      └────────┼─────────────────┼──────────┘              │
│                               │                 │                         │
│  ┌────────────────────────────▼─────────────────▼──────────────────┐      │
│  │                                                                 │      │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐    │      │
│  │  │  RDS          │  │ ElastiCache  │  │  MSK Serverless    │    │      │
│  │  │  PostgreSQL   │  │  Redis       │  │  (Kafka)           │    │      │
│  │  │  db.t3.micro  │  │ cache.t3.micro│ │  ⚠️ 프리티어 없음   │    │      │
│  │  │  ✅ 프리티어   │  │ ⚠️ 유료      │  │  → 로컬 Kafka 유지 │    │      │
│  │  │  20GB         │  │              │  │                    │    │      │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘    │      │
│  │                                                                 │      │
│  └─────────────────────────────────────────────────────────────────┘      │
│                                                                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                     │
│  │  S3          │  │  CloudWatch  │  │  ECR         │                     │
│  │  React Build │  │  로그/메트릭  │  │  Docker Image│                     │
│  │  ✅ 프리티어  │  │  ✅ 프리티어  │  │  ✅ 프리티어  │                     │
│  └──────────────┘  └──────────────┘  └──────────────┘                     │
└────────────────────────────────────────────────────────────────────────────┘
```

## 3. AWS 비용 산정 (월간)

### 프리티어 범위 (12개월)

| 서비스 | 프리티어 제공 | 우리 사용량 | 월 비용 |
|--------|-------------|-----------|--------|
| **EC2 (ECS Fargate)** | ❌ 프리티어 없음 | 0.5 vCPU, 1GB | ~$15 |
| **RDS PostgreSQL** | ✅ db.t3.micro 750시간 | db.t3.micro | **$0** |
| **ElastiCache** | ❌ 프리티어 없음 | — | — |
| **S3** | ✅ 5GB, 20k GET | React Build ~50MB | **$0** |
| **CloudFront** | ✅ 1TB/월 | ~10GB | **$0** |
| **ECR** | ✅ 500MB | ~300MB | **$0** |
| **CloudWatch** | ✅ 기본 메트릭 | 기본 | **$0** |
| **Route 53** | ❌ | 1 호스팅 존 | ~$0.5 |
| **ALB** | ❌ 프리티어 없음 | — | — |

### 비용 최적화 전략

```
⚠️ 핵심 원칙: 사이드 프로젝트 = 최소 비용

Phase 1-5 (개발 기간): $0 (전부 로컬 Docker)
Phase 6 (배포 시):
```

| 전략 | 적용 | 예상 절감 |
|------|------|----------|
| **ECS → EC2 t3.micro (프리티어)** | App + ML을 1개 EC2에 직접 배포 | Fargate 비용 제거 → **$0** |
| **ElastiCache → EC2 내 Redis** | 같은 t3.micro에 Redis 설치 | ElastiCache 비용 제거 → **$0** |
| **ALB → Nginx (EC2 내)** | EC2에 Nginx 리버스 프록시 | ALB 비용 제거 → **$0** |
| **MSK → 없음** | Kafka는 로컬에서만 사용, 프로덕션은 SQS로 대체 | MSK 비용 제거 → **$0** |
| **Billing Alert 설정** | $5, $10, $25 알림 | 과금 폭탄 방지 |

### 최소 비용 배포 아키텍처 (월 ~$1)

```
EC2 t3.micro (프리티어)
├── Docker Compose
│   ├── pulseq-app (Spring Boot)
│   ├── pulseq-ml (FastAPI)
│   ├── Redis (컨테이너)
│   └── Nginx
├── RDS PostgreSQL (프리티어)
├── S3 (React 정적 호스팅, 프리티어)
└── Route 53 ($0.5/월)

→ 총 월 비용: ~$0.5 (Route 53만)
→ 프리티어 만료 후: ~$10-15/월
```

## 4. Billing Alert 설정 (필수)

```bash
# AWS CLI로 Billing Alert 설정
aws cloudwatch put-metric-alarm \
  --alarm-name "PulseQ-Budget-5USD" \
  --alarm-description "월 비용 $5 초과 경고" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 21600 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:{account}:billing-alert \
  --dimensions Name=Currency,Value=USD

# $10, $25 알림도 동일하게 설정
```

## 5. CI/CD 파이프라인

```
┌──────────────────────────────────────────────────────────┐
│                   GitHub Actions                          │
│                                                           │
│  Push to main                                             │
│    │                                                      │
│    ├─ 1. Test (Unit + Integration)                        │
│    │     └─ ./gradlew test (JaCoCo 80%+ 검증)            │
│    │                                                      │
│    ├─ 2. Build Docker Image                               │
│    │     └─ docker build → ECR push                       │
│    │                                                      │
│    ├─ 3. Deploy (EC2)                                     │
│    │     └─ SSH → docker compose pull → up -d             │
│    │                                                      │
│    └─ 4. Health Check                                     │
│          └─ curl /health → 200 OK 확인                    │
│                                                           │
│  PR 생성 시                                                │
│    └─ Test + Lint + Build (배포 안 함)                     │
└──────────────────────────────────────────────────────────┘
```

## 6. 모니터링 구성

### Prometheus 메트릭

```yaml
# 수집 대상
scrape_configs:
  - job_name: 'pulseq-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['pulseq-app:8080']

  - job_name: 'pulseq-ml'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['pulseq-ml:8000']
```

### 핵심 대시보드 (Grafana)

| 패널 | 메트릭 | 알림 조건 |
|------|--------|----------|
| API 레이턴시 | `http_server_requests_seconds` | p99 > 200ms |
| 대기열 크기 | `pulseq_queue_size` | > 100,000 |
| TPS | `http_server_requests_seconds_count` rate | < 10 (서비스 이상) |
| Redis 연결 | `redis_connections_active` | 0 (연결 끊김) |
| Kafka Consumer Lag | `kafka_consumer_lag` | > 10,000 |
| 봇 차단률 | `pulseq_bot_blocked_total` rate | > 30% (오탐 의심) |
| JVM 힙 | `jvm_memory_used_bytes` | > 80% |
