CREATE TABLE events (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID         NOT NULL REFERENCES tenants(id),
    name                     VARCHAR(255) NOT NULL,
    slug                     VARCHAR(255) NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    max_capacity             INT          NOT NULL,
    rate_limit               INT          NOT NULL DEFAULT 100,
    entry_token_ttl_seconds  INT          NOT NULL DEFAULT 300,
    start_at                 TIMESTAMPTZ  NOT NULL,
    end_at                   TIMESTAMPTZ  NOT NULL,
    bot_detection_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    bot_score_threshold      DECIMAL(3,2) NOT NULL DEFAULT 0.80,
    webhook_url              VARCHAR(500),
    total_entered            INT          NOT NULL DEFAULT 0,
    total_processed          INT          NOT NULL DEFAULT 0,
    total_abandoned          INT          NOT NULL DEFAULT 0,
    total_bot_blocked        INT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_events_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_events_capacity CHECK (max_capacity > 0),
    CONSTRAINT chk_events_time CHECK (end_at > start_at),
    CONSTRAINT uq_events_tenant_slug UNIQUE (tenant_id, slug)
);

CREATE INDEX idx_events_tenant_id ON events(tenant_id);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_start_at ON events(start_at);
