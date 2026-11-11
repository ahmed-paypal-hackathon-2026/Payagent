-- Align the remaining fixed-length CHAR columns with canonical VARCHAR String mappings.
-- Forward-only; preserves existing values; safe on fresh databases and on
-- databases that applied V1..V4.
ALTER TABLE mandates
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING btrim(currency::text);

ALTER TABLE invoices
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING btrim(currency::text);

ALTER TABLE payments
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING btrim(currency::text);
