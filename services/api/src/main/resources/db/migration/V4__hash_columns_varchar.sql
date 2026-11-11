-- Align the remaining hash columns with canonical VARCHAR(64) String mappings.
-- Forward-only; preserves existing hash values; safe on fresh databases and
-- on databases that applied V1..V3.
ALTER TABLE evidence
    ALTER COLUMN sha256_hash TYPE VARCHAR(64)
    USING btrim(sha256_hash::text);

ALTER TABLE idempotency_keys
    ALTER COLUMN request_hash TYPE VARCHAR(64)
    USING btrim(request_hash::text);
