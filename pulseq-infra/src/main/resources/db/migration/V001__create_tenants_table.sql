CREATE TABLE tenants (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                  VARCHAR(255) NOT NULL UNIQUE,
    password_hash          VARCHAR(255) NOT NULL,
    company_name           VARCHAR(255) NOT NULL,
    plan                   VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    api_key                VARCHAR(100) NOT NULL,
    api_key_hash           VARCHAR(255) NOT NULL UNIQUE,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    rate_limit_per_second  INT          NOT NULL DEFAULT 10,
    monthly_quota          INT          NOT NULL DEFAULT 10000,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_tenants_plan CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    CONSTRAINT chk_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX idx_tenants_email ON tenants(email);
CREATE INDEX idx_tenants_api_key_hash ON tenants(api_key_hash);
CREATE INDEX idx_tenants_status ON tenants(status);
