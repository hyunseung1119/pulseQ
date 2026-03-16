CREATE TABLE queue_entries (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                 UUID         NOT NULL REFERENCES events(id),
    user_id                  VARCHAR(255) NOT NULL,
    queue_ticket             VARCHAR(64)  NOT NULL UNIQUE,
    position                 BIGINT       NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
    bot_score                DOUBLE PRECISION,
    blocked_reason           VARCHAR(100),
    ip_address               VARCHAR(45),
    user_agent               TEXT,
    fingerprint              VARCHAR(255),
    entered_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at             TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    entry_token              VARCHAR(255),
    entry_token_expires_at   TIMESTAMPTZ,

    CONSTRAINT chk_queue_status CHECK (status IN ('WAITING', 'PROCESSING', 'COMPLETED', 'ABANDONED', 'BLOCKED')),
    CONSTRAINT uq_queue_event_user UNIQUE (event_id, user_id)
);

CREATE INDEX idx_queue_entries_event_status ON queue_entries(event_id, status);
CREATE INDEX idx_queue_entries_ticket ON queue_entries(queue_ticket);
CREATE INDEX idx_queue_entries_entered_at ON queue_entries(entered_at);
