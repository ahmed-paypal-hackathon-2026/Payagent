-- Durable capture-attempt correlation. Supports "uncertain capture outcome"
-- reconciliation without blindly re-capturing. Forward-only.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS capture_request_id VARCHAR(128);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS capture_resolution VARCHAR(32) NOT NULL DEFAULT 'NONE';

CREATE INDEX IF NOT EXISTS idx_payments_capture_resolution
    ON payments (capture_resolution, capture_attempted_at);
