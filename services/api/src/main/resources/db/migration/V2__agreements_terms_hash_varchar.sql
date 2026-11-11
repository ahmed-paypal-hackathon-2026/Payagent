-- Align agreements.terms_hash_sha256 with canonical VARCHAR(64) mapping (forward-only).
-- Preserves existing hash values; safe for already-applied V1 databases.
ALTER TABLE agreements
    ALTER COLUMN terms_hash_sha256 TYPE VARCHAR(64)
    USING btrim(terms_hash_sha256::text);
