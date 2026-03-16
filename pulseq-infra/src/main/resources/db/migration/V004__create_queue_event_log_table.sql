CREATE TABLE queue_event_log (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL,
    user_id         VARCHAR(255),
    event_type      VARCHAR(50) NOT NULL
                    CHECK (event_type IN (
                        'QUEUE_ENTERED', 'POSITION_UPDATED', 'ENTRY_GRANTED',
                        'ENTRY_VERIFIED', 'ENTRY_EXPIRED', 'QUEUE_LEFT',
                        'BOT_DETECTED', 'BOT_BLOCKED'
                    )),
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 시계열 쿼리 최적화
CREATE INDEX idx_event_log_event_time ON queue_event_log(event_id, created_at);
CREATE INDEX idx_event_log_type ON queue_event_log(event_type);
CREATE INDEX idx_event_log_created_at ON queue_event_log(created_at);

-- BRIN 인덱스 (시계열 데이터에 효율적)
CREATE INDEX idx_event_log_created_brin ON queue_event_log
    USING BRIN (created_at) WITH (pages_per_range = 32);
