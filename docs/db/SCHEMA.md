# PulseQ — DB 스키마 설계

## ERD 개요

```
┌──────────────┐     ┌──────────────┐     ┌──────────────────┐
│   tenants    │──┐  │   events     │──┐  │  queue_entries   │
│              │  │  │              │  │  │                  │
│ PK id        │  │  │ PK id        │  │  │ PK id            │
│    email     │  └──│ FK tenant_id │  └──│ FK event_id      │
│    password  │     │    name      │     │    user_id       │
│    company   │     │    slug      │     │    position      │
│    plan      │     │    status    │     │    status        │
│    api_key   │     │    max_cap   │     │    entered_at    │
└──────────────┘     │    start_at  │     │    processed_at  │
                     │    end_at    │     │    bot_score     │
                     └──────────────┘     └──────────────────┘
                                                    │
                     ┌──────────────┐     ┌─────────▼────────┐
                     │  api_usage   │     │  queue_event_log │
                     │              │     │                  │
                     │ PK id        │     │ PK id            │
                     │ FK tenant_id │     │ FK event_id      │
                     │    endpoint  │     │    user_id       │
                     │    count     │     │    event_type    │
                     │    date      │     │    payload       │
                     └──────────────┘     │    created_at    │
                                          └──────────────────┘
```

## 테이블 정의

### 1. tenants — 테넌트 (고객사)
```sql
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    company_name    VARCHAR(255) NOT NULL,
    plan            VARCHAR(20)  NOT NULL DEFAULT 'FREE'
                    CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    api_key         VARCHAR(64)  NOT NULL UNIQUE,
    api_key_hash    VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    rate_limit_per_second  INT  NOT NULL DEFAULT 10,
    monthly_quota          INT  NOT NULL DEFAULT 10000,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenants_email ON tenants(email);
CREATE INDEX idx_tenants_api_key_hash ON tenants(api_key_hash);
CREATE INDEX idx_tenants_status ON tenants(status);
```

### 2. events — 대기열 이벤트
```sql
CREATE TABLE events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED'
                    CHECK (status IN ('SCHEDULED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    max_capacity    INT NOT NULL CHECK (max_capacity > 0),
    rate_limit      INT NOT NULL DEFAULT 100,
    entry_token_ttl_seconds INT NOT NULL DEFAULT 300,
    start_at        TIMESTAMPTZ NOT NULL,
    end_at          TIMESTAMPTZ NOT NULL,
    bot_detection_enabled   BOOLEAN NOT NULL DEFAULT true,
    bot_score_threshold     DECIMAL(3,2) NOT NULL DEFAULT 0.80,
    webhook_url     VARCHAR(500),
    total_entered   INT NOT NULL DEFAULT 0,
    total_processed INT NOT NULL DEFAULT 0,
    total_abandoned INT NOT NULL DEFAULT 0,
    total_bot_blocked INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, slug),
    CHECK (end_at > start_at)
);

CREATE INDEX idx_events_tenant_id ON events(tenant_id);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_start_at ON events(start_at);
CREATE INDEX idx_events_tenant_slug ON events(tenant_id, slug);
```

### 3. queue_entries — 대기열 항목 (영구 기록)
```sql
CREATE TABLE queue_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL REFERENCES events(id),
    user_id         VARCHAR(255) NOT NULL,
    queue_ticket    VARCHAR(64)  NOT NULL UNIQUE,
    position        INT NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'WAITING'
                    CHECK (status IN ('WAITING', 'PROCESSING', 'COMPLETED', 'ABANDONED', 'BLOCKED')),
    bot_score       DECIMAL(5,4),
    blocked_reason  VARCHAR(100),
    ip_address      INET,
    user_agent      TEXT,
    fingerprint     VARCHAR(255),
    entered_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    entry_token     VARCHAR(255),
    entry_token_expires_at TIMESTAMPTZ
);

-- 복합 인덱스: 이벤트별 사용자 중복 방지
CREATE UNIQUE INDEX idx_queue_entries_event_user ON queue_entries(event_id, user_id);
CREATE INDEX idx_queue_entries_event_status ON queue_entries(event_id, status);
CREATE INDEX idx_queue_entries_ticket ON queue_entries(queue_ticket);
CREATE INDEX idx_queue_entries_entered_at ON queue_entries(entered_at);

-- 파티셔닝 고려 (대규모 데이터 시)
-- PARTITION BY RANGE (entered_at);
```

### 4. queue_event_log — 이벤트 로그 (Kafka → DB)
```sql
CREATE TABLE queue_event_log (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL,
    user_id         VARCHAR(255),
    event_type      VARCHAR(50)  NOT NULL
                    CHECK (event_type IN (
                        'QUEUE_ENTERED', 'POSITION_UPDATED', 'ENTRY_GRANTED',
                        'ENTRY_VERIFIED', 'ENTRY_EXPIRED', 'QUEUE_LEFT',
                        'BOT_DETECTED', 'BOT_BLOCKED'
                    )),
    payload         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 시계열 쿼리 최적화
CREATE INDEX idx_event_log_event_time ON queue_event_log(event_id, created_at);
CREATE INDEX idx_event_log_type ON queue_event_log(event_type);
CREATE INDEX idx_event_log_created_at ON queue_event_log(created_at);

-- BRIN 인덱스 (시계열 데이터에 효율적)
CREATE INDEX idx_event_log_created_brin ON queue_event_log
    USING BRIN (created_at) WITH (pages_per_range = 32);
```

### 5. api_usage — API 사용량 추적
```sql
CREATE TABLE api_usage (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    usage_date      DATE NOT NULL,
    endpoint        VARCHAR(100) NOT NULL,
    request_count   BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, usage_date, endpoint)
);

CREATE INDEX idx_api_usage_tenant_date ON api_usage(tenant_id, usage_date);
```

## Redis 데이터 구조

### 대기열 (Sorted Set)
```redis
# 키: queue:{eventId}
# Score: 입장 시각 (Unix timestamp ms)
# Member: userId

ZADD queue:evt_bts2026 1711940401000 "user_12345"
ZADD queue:evt_bts2026 1711940401001 "user_12346"

# 순번 조회
ZRANK queue:evt_bts2026 "user_12345"  → 0 (1번째)

# 다음 처리 대상 N명 추출
ZPOPMIN queue:evt_bts2026 100  → 상위 100명 pop
```

### 입장 토큰 (String + TTL)
```redis
# 키: entry_token:{token}
# Value: JSON (userId, eventId, grantedAt)
# TTL: 300초 (5분)

SET entry_token:et_abc123 '{"userId":"user_12345","eventId":"evt_bts2026"}' EX 300
```

### 봇 스코어 캐시 (String + TTL)
```redis
# 키: bot_score:{eventId}:{userId}
# Value: 스코어 (0.0 ~ 1.0)
# TTL: 600초 (10분)

SET bot_score:evt_bts2026:user_12345 "0.15" EX 600
```

### Rate Limiting (Sliding Window)
```redis
# 키: rate:{tenantId}:{windowStart}
# 구현: Redis Lua 스크립트로 원자적 처리

MULTI
  INCR rate:tnt_abc123:1711940400
  EXPIRE rate:tnt_abc123:1711940400 2
EXEC
```

### 분산 락 (Redisson)
```redis
# 키: lock:queue:{eventId}
# Redisson이 내부적으로 관리

RLock lock = redisson.getLock("lock:queue:evt_bts2026");
lock.tryLock(5, 10, TimeUnit.SECONDS);
```

### 이벤트 메타 캐시 (Hash)
```redis
# 키: event_meta:{eventId}

HSET event_meta:evt_bts2026
    status "ACTIVE"
    maxCapacity "50000"
    currentSize "15000"
    rateLimit "100"
    botEnabled "true"
    botThreshold "0.8"
```

## Kafka 토픽 설계

| 토픽 | 파티션 | 리텐션 | 용도 |
|------|--------|--------|------|
| `pulseq.queue.events` | 6 | 7일 | 대기열 입장/처리/이탈 이벤트 |
| `pulseq.bot.events` | 3 | 7일 | 봇 탐지 요청/결과 |
| `pulseq.metrics` | 3 | 1일 | 실시간 메트릭 |

### 메시지 형식 (Avro/JSON)
```json
// pulseq.queue.events
{
    "eventId": "evt_bts2026",
    "userId": "user_12345",
    "type": "QUEUE_ENTERED",
    "timestamp": "2026-04-01T10:00:01Z",
    "metadata": {
        "position": 1523,
        "ip": "203.0.113.1",
        "userAgent": "Mozilla/5.0..."
    }
}
```

## 데이터 보존 정책

| 데이터 | 보존 기간 | 사유 |
|--------|----------|------|
| tenants | 영구 | 계정 정보 |
| events | 영구 | 이벤트 이력 |
| queue_entries | 90일 | 분석/감사용 후 아카이브 |
| queue_event_log | 30일 | 분석 후 삭제 (Kafka에서 리플레이 가능) |
| api_usage | 12개월 | 과금 정산 |
| Redis 캐시 | TTL 기반 | 자동 만료 |

## 마이그레이션 전략

- **도구**: Flyway (Spring Boot 기본 통합)
- **네이밍**: `V{번호}__{설명}.sql` (예: `V001__create_tenants_table.sql`)
- **규칙**: DDL은 반드시 마이그레이션으로만 변경, 수동 ALTER 금지
