# Canonical JSON fixtures (Member F)

Synthetic test data only — no real identities, tokens, or PayPal production IDs.

| File | Purpose |
|------|---------|
| `01-buyer-intake.json` | `StructuredTermsDTO` extracted from buyer brief (AI intake baseline) |
| `02-seller-counteroffer.json` | Canonical `OfferDTO` — seller round 1 |
| `03-over-budget-offer.json` | Mandate violation probe (`mandateCompliant: false`) |
| `04-prompt-injection-payload.json` | Adversarial LLM input corpus entry |
| `05-post-acceptance-edit.json` | Agreement hash invalidation scenario metadata |
| `06-accepted-agreement-snapshot.json` | Dual-consent `AgreementSnapshotDTO` |
| `07-settlement-invoice-and-authorization.json` | Dual-rail `SettlementStateDTO` (invoice SENT + auth hold) |
| `08-duplicate-webhook-event.json` | PayPal webhook deduplication scenario |
| `09-deliverable-evidence.json` | `DeliverableEvidenceDTO` with SHA-256 metadata |
| `10-active-dispute-workspace.json` | `DealWorkspaceDTO` in `DISPUTED` / capture freeze |

Validate syntax from repo root:

```cmd
node scripts\validate-fixtures.mjs
```

The accepted agreement fixture contains the SHA-256 of its actual canonical terms. Its hash is checked by `FixtureReleaseGateTest`, alongside DTO validation, deal/version/amount consistency, and duplicate-event identifiers. PayPal IDs, download signatures, and evidence digests remain synthetic labels: these fixtures do not establish a live payment or a valid file download.

## DTO review handoff

Member F inspected the Java DTO records, TypeScript mirror and plan contracts locally. The automated enum parity test and fixture deserialization checks pass; this is not a substitute for A/D approving shared contract changes. No Java/TypeScript contract changes were made by F. A/D can review the canonical fixtures on `member-f/release-gates`; coordination is tracked in GitHub WP-A1 and WP-D1 issues. Keep fixture IDs and signatures clearly synthetic; replace them only in private disposable integration data.
