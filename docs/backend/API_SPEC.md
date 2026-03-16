# PulseQ — API 명세서

## Base URL
```
Local:      http://localhost:8080/api/v1
Production: https://api.pulseq.io/v1
```

## 인증
```
# 테넌트 API 호출 시
X-API-Key: pq_live_abc123def456

# 대시보드 로그인 후
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

## 공통 응답 형식

### 성공
```json
{
    "success": true,
    "data": { ... },
    "timestamp": "2026-03-16T12:00:00Z"
}
```

### 에러 (RFC 9457)
```json
{
    "type": "https://pulseq.io/errors/not-found",
    "title": "Resource Not Found",
    "status": 404,
    "detail": "Event with id 'evt_123' not found",
    "instance": "/api/v1/events/evt_123"
}
```

---

## 1. 테넌트 (Tenant) API

### POST /tenants/signup — 회원가입
```
Request:
{
    "email": "admin@company.com",
    "password": "securePassword123!",
    "companyName": "TicketCorp",
    "plan": "FREE"
}

Response (201):
{
    "success": true,
    "data": {
        "tenantId": "tnt_abc123",
        "email": "admin@company.com",
        "companyName": "TicketCorp",
        "plan": "FREE",
        "apiKey": "pq_live_abc123def456",
        "createdAt": "2026-03-16T12:00:00Z"
    }
}
```

### POST /tenants/login — 로그인
```
Request:
{
    "email": "admin@company.com",
    "password": "securePassword123!"
}

Response (200):
{
    "success": true,
    "data": {
        "accessToken": "eyJhbGciOi...",
        "refreshToken": "eyJhbGciOi...",
        "expiresIn": 86400
    }
}
```

### GET /tenants/me — 내 정보 조회
```
Headers: Authorization: Bearer {token}

Response (200):
{
    "success": true,
    "data": {
        "tenantId": "tnt_abc123",
        "email": "admin@company.com",
        "companyName": "TicketCorp",
        "plan": "FREE",
        "usage": {
            "currentMonth": 8500,
            "limit": 10000
        }
    }
}
```

### POST /tenants/api-keys/rotate — API 키 재발급
```
Headers: Authorization: Bearer {token}

Response (200):
{
    "success": true,
    "data": {
        "apiKey": "pq_live_newkey789",
        "previousKeyExpiresAt": "2026-03-17T12:00:00Z"
    }
}
```

---

## 2. 이벤트 (Event) API

### POST /events — 이벤트 생성
```
Headers: X-API-Key: {apiKey}

Request:
{
    "name": "BTS 서울 콘서트 2026",
    "slug": "bts-seoul-2026",
    "maxCapacity": 50000,
    "startAt": "2026-04-01T10:00:00+09:00",
    "endAt": "2026-04-01T12:00:00+09:00",
    "rateLimit": 100,
    "entryTokenTtlSeconds": 300,
    "config": {
        "botDetectionEnabled": true,
        "botScoreThreshold": 0.8,
        "webhookUrl": "https://company.com/webhook/queue-complete"
    }
}

Response (201):
{
    "success": true,
    "data": {
        "eventId": "evt_bts2026",
        "name": "BTS 서울 콘서트 2026",
        "slug": "bts-seoul-2026",
        "status": "SCHEDULED",
        "maxCapacity": 50000,
        "startAt": "2026-04-01T10:00:00+09:00",
        "endAt": "2026-04-01T12:00:00+09:00",
        "rateLimit": 100,
        "entryTokenTtlSeconds": 300,
        "createdAt": "2026-03-16T12:00:00Z"
    }
}
```

### GET /events — 이벤트 목록 조회
```
Headers: X-API-Key: {apiKey}
Query: ?status=ACTIVE&page=0&size=20

Response (200):
{
    "success": true,
    "data": {
        "content": [ ... ],
        "page": 0,
        "size": 20,
        "totalElements": 5,
        "totalPages": 1
    }
}
```

### GET /events/{eventId} — 이벤트 상세 조회
### PATCH /events/{eventId} — 이벤트 수정
### DELETE /events/{eventId} — 이벤트 삭제

---

## 3. 대기열 (Queue) API — 핵심

### POST /queues/{eventId}/enter — 대기열 입장
```
Headers: X-API-Key: {apiKey}

Request:
{
    "userId": "user_12345",
    "metadata": {
        "ip": "203.0.113.1",
        "userAgent": "Mozilla/5.0...",
        "fingerprint": "fp_abc123"
    }
}

Response (201):
{
    "success": true,
    "data": {
        "queueTicket": "qt_xyz789",
        "position": 1523,
        "estimatedWaitSeconds": 152,
        "totalAhead": 1522,
        "status": "WAITING",
        "enteredAt": "2026-04-01T10:00:01Z"
    }
}

Error (429 — 봇 탐지):
{
    "type": "https://pulseq.io/errors/bot-detected",
    "title": "Suspicious Activity Detected",
    "status": 429,
    "detail": "Request blocked due to suspicious behavior pattern",
    "extensions": {
        "botScore": 0.92,
        "reason": "RAPID_CLICK_PATTERN"
    }
}

Error (409 — 이미 입장):
{
    "type": "https://pulseq.io/errors/already-in-queue",
    "title": "Already In Queue",
    "status": 409,
    "detail": "User 'user_12345' is already in the queue",
    "extensions": {
        "existingTicket": "qt_xyz789",
        "currentPosition": 1523
    }
}
```

### GET /queues/{eventId}/position/{queueTicket} — 순번 조회
```
Headers: X-API-Key: {apiKey}

Response (200):
{
    "success": true,
    "data": {
        "queueTicket": "qt_xyz789",
        "position": 823,
        "estimatedWaitSeconds": 82,
        "totalAhead": 822,
        "totalBehind": 4177,
        "status": "WAITING",
        "ratePerSecond": 100
    }
}
```

### GET /queues/{eventId}/status — 대기열 현황
```
Headers: X-API-Key: {apiKey}

Response (200):
{
    "success": true,
    "data": {
        "eventId": "evt_bts2026",
        "status": "ACTIVE",
        "totalWaiting": 5000,
        "totalProcessed": 15000,
        "totalAbandoned": 320,
        "currentRatePerSecond": 100,
        "estimatedClearTimeSeconds": 50,
        "botBlocked": 142
    }
}
```

### POST /queues/{eventId}/verify — 입장 토큰 검증
```
Headers: X-API-Key: {apiKey}

Request:
{
    "entryToken": "et_abc123xyz"
}

Response (200):
{
    "success": true,
    "data": {
        "valid": true,
        "userId": "user_12345",
        "expiresAt": "2026-04-01T10:05:01Z",
        "remainingSeconds": 245
    }
}
```

### DELETE /queues/{eventId}/leave/{queueTicket} — 대기열 이탈
```
Headers: X-API-Key: {apiKey}

Response (200):
{
    "success": true,
    "data": {
        "queueTicket": "qt_xyz789",
        "status": "LEFT",
        "leftAt": "2026-04-01T10:03:00Z"
    }
}
```

---

## 4. WebSocket — 실시간 순번 업데이트

### 연결
```
ws://localhost:8080/ws/queues/{eventId}?ticket={queueTicket}&apiKey={apiKey}
```

### 서버 → 클라이언트 메시지
```json
// 순번 업데이트 (5초 간격)
{
    "type": "POSITION_UPDATE",
    "data": {
        "position": 500,
        "estimatedWaitSeconds": 50,
        "totalAhead": 499
    }
}

// 입장 허가
{
    "type": "ENTRY_GRANTED",
    "data": {
        "entryToken": "et_abc123xyz",
        "expiresAt": "2026-04-01T10:05:01Z",
        "redirectUrl": "https://company.com/checkout?token=et_abc123xyz"
    }
}

// 이벤트 종료
{
    "type": "EVENT_ENDED",
    "data": {
        "reason": "CAPACITY_REACHED"
    }
}
```

---

## 5. 통계 (Stats) API

### GET /stats/{eventId}/realtime — 실시간 통계
```
Headers: Authorization: Bearer {token}

Response (200):
{
    "success": true,
    "data": {
        "currentWaiting": 5000,
        "processedTotal": 15000,
        "processedLastMinute": 6000,
        "abandonedTotal": 320,
        "abandonRate": 0.016,
        "avgWaitSeconds": 45.2,
        "p99WaitSeconds": 120,
        "botBlockedTotal": 142,
        "botBlockRate": 0.007
    }
}
```

### GET /stats/{eventId}/timeline — 시간대별 통계
```
Headers: Authorization: Bearer {token}
Query: ?interval=1m&from=2026-04-01T10:00:00Z&to=2026-04-01T10:30:00Z

Response (200):
{
    "success": true,
    "data": {
        "intervals": [
            {
                "timestamp": "2026-04-01T10:00:00Z",
                "entered": 3200,
                "processed": 100,
                "abandoned": 15,
                "botBlocked": 22
            },
            ...
        ]
    }
}
```

---

## 6. 헬스체크

### GET /health
```
Response (200):
{
    "status": "UP",
    "components": {
        "postgres": "UP",
        "redis": "UP",
        "kafka": "UP",
        "mlService": "UP"
    },
    "version": "1.0.0"
}
```

---

## Rate Limiting

| 플랜 | 제한 | 초과 시 |
|------|------|--------|
| FREE | 10,000 req/월, 10 req/s | 429 Too Many Requests |
| PRO | 1,000,000 req/월, 1,000 req/s | 429 Too Many Requests |

### Rate Limit 응답 헤더
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
X-RateLimit-Reset: 1711900800
```
