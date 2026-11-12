# Test Skip Analysis — 11 skipped tests

## Breakdown
- `GeminiLiveIntegrationTest` ×4 — external AI endpoint/credentials unavailable. Environment unavailable.
- `FlywayPostgreSqlVerificationTest` ×2 — disposable PG env vars absent in default run. Environment unavailable; 6/6 pass when provided.
- `PayPalEventsPostgreSqlConcurrencyTest` ×2 — same.
- `SettlementWebhookPostgreSqlServiceTest` ×2 — now `@EnabledIfEnvironmentVariable(named = "PAYAGENT_B17_SERVICE_URL")`. Environment unavailable for default suite; pass with disposable PG.
- `ReleaseGateSuitePlaceholderTest` ×1 — intentional placeholder gate.

## Classification
- Environment unavailable: 10
- Intentionally placeholder: 1
- Masking a regression: 0 identified

Counts before fix: 5 skipped; after B22.2 PG test tagging: 11 skipped. The additional 6 skips are explicit, not hidden failures.
