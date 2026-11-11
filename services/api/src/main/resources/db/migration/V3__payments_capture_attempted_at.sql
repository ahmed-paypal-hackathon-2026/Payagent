-- Record PayPal capture intent before the network call so scheduled
-- reconciliation can recover a captured-but-not-persisted authorization.
-- Forward-only; safe on fresh databases and V1+V2 databases.
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS capture_attempted_at TIMESTAMPTZ;
