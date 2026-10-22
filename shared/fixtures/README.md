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
