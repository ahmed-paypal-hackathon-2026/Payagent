-- Bootstrap schema scaffold (Member A owns evolution). Aligns with plan entity list.

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deals (
    id              UUID PRIMARY KEY,
    buyer_user_id   UUID NOT NULL REFERENCES users (id),
    seller_user_id  UUID REFERENCES users (id),
    status          VARCHAR(64) NOT NULL,
    stop_reason     VARCHAR(64) NOT NULL DEFAULT 'NONE',
    invite_token_hash VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE mandates (
    id                      UUID PRIMARY KEY,
    deal_id                 UUID NOT NULL REFERENCES deals (id),
    party_role              VARCHAR(16) NOT NULL,
    version                 INT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    limit_price_minor       BIGINT NOT NULL,
    target_price_minor      BIGINT NOT NULL,
    limit_delivery_days     INT NOT NULL,
    target_delivery_days    INT NOT NULL,
    limit_revisions         INT NOT NULL,
    target_revisions        INT NOT NULL,
    must_have_deliverables  JSONB NOT NULL DEFAULT '[]',
    forbidden_terms         JSONB NOT NULL DEFAULT '[]',
    mode                    VARCHAR(32) NOT NULL,
    max_rounds              INT NOT NULL,
    expires_at              TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (deal_id, party_role, version)
);

CREATE TABLE offers (
    id                  UUID PRIMARY KEY,
    deal_id             UUID NOT NULL REFERENCES deals (id),
    round_number        INT NOT NULL,
    proposer_role       VARCHAR(16) NOT NULL,
    generated_by_agent  BOOLEAN NOT NULL DEFAULT FALSE,
    terms_json          JSONB NOT NULL,
    rationale_summary   VARCHAR(500) NOT NULL,
    caller_utility_score VARCHAR(32) NOT NULL,
    mandate_compliant   BOOLEAN NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE agreements (
    id                  UUID PRIMARY KEY,
    deal_id             UUID NOT NULL REFERENCES deals (id),
    version             INT NOT NULL,
    canonical_terms_json JSONB NOT NULL,
    terms_hash_sha256   VARCHAR(64) NOT NULL,
    buyer_accepted_at   TIMESTAMPTZ,
    seller_accepted_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (deal_id, version)
);

CREATE TABLE invoices (
    id                      UUID PRIMARY KEY,
    deal_id                 UUID NOT NULL REFERENCES deals (id),
    agreement_version       INT NOT NULL,
    lifecycle_status        VARCHAR(32) NOT NULL,
    paypal_invoice_id       VARCHAR(128),
    paypal_invoice_number   VARCHAR(128),
    paypal_invoice_view_url TEXT,
    amount_minor            BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payments (
    id                      UUID PRIMARY KEY,
    deal_id                 UUID NOT NULL REFERENCES deals (id),
    agreement_version       INT NOT NULL,
    lifecycle_status        VARCHAR(32) NOT NULL,
    paypal_order_id         VARCHAR(128),
    paypal_authorization_id VARCHAR(128),
    paypal_capture_id       VARCHAR(128),
    authorization_expires_at TIMESTAMPTZ,
    amount_minor            BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE evidence (
    id              UUID PRIMARY KEY,
    deal_id         UUID NOT NULL REFERENCES deals (id),
    uploader_role   VARCHAR(16) NOT NULL,
    filename        VARCHAR(512) NOT NULL,
    mime_type       VARCHAR(128) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    sha256_hash     VARCHAR(64) NOT NULL,
    version         INT NOT NULL,
    storage_key     VARCHAR(1024) NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE disputes (
    id              UUID PRIMARY KEY,
    deal_id         UUID NOT NULL REFERENCES deals (id),
    status          VARCHAR(64) NOT NULL,
    reason          TEXT NOT NULL,
    resolution_request TEXT,
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ
);

CREATE TABLE paypal_events (
    event_id        VARCHAR(128) PRIMARY KEY,
    event_type      VARCHAR(128) NOT NULL,
    resource_id     VARCHAR(128),
    payload_json    JSONB NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,
    deal_id         UUID NOT NULL REFERENCES deals (id),
    seq             BIGINT NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload_json    JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (deal_id, seq)
);

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(128) NOT NULL,
    deal_id         UUID,
    route           VARCHAR(256) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (idempotency_key, route)
);

CREATE INDEX idx_deals_buyer ON deals (buyer_user_id);
CREATE INDEX idx_deals_seller ON deals (seller_user_id);
CREATE INDEX idx_offers_deal ON offers (deal_id);
CREATE INDEX idx_audit_deal_seq ON audit_events (deal_id, seq);
