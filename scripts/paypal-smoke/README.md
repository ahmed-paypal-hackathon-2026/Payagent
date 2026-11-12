# PayPal Sandbox Smoke Proof Scripts (PP-G1 to PP-G4)

This directory contains standalone scripts to verify live PayPal Sandbox connectivity without requiring the backend database or full application stack.

## Milestones Covered
- **PP-G1**: Live OAuth2 `client_credentials` token retrieval with Sandbox API.
- **PP-G2**: Invoicing v2 draft creation with `termsHashSha256` embedded in `detail.note`, amount exact to the cent, and invoice dispatch via `/send`.
- **PP-G3**: Orders v2 creation with `intent: AUTHORIZE`, linked `invoice_id`, buyer approval, and authorization into an `AUTH-...` hold.
- **PP-G4**: Capture the authorization (`CAP-...`) and record the invoice payment, or void the authorization and cancel the invoice.

## Prerequisites
1. PayPal Developer Sandbox Account (Business & Personal): [https://developer.paypal.com](https://developer.paypal.com)
2. Sandbox App Client ID and Secret with **Invoicing** and **Orders** capabilities enabled.
3. Node.js 18+ (tested on Node v24).
4. Sandbox Business and Personal account emails for a reliable invoice send and buyer approval.

## Running the Smoke Script

Set your credentials in your shell or `.env`:
```bash
export PAYPAL_CLIENT_ID="your_sandbox_client_id"
export PAYPAL_CLIENT_SECRET="your_sandbox_client_secret"
export PAYPAL_API_BASE_URL="https://api-m.sandbox.paypal.com" # Required Sandbox Host
export PAYPAL_INVOICER_EMAIL="sb-business@business.example.com"
export PAYPAL_PAYER_EMAIL="sb-personal@personal.example.com"
```

Create the invoice/order only (PP-G1 to PP-G3a):
```bash
node scripts/paypal-smoke/smoke-proof.mjs
```
This prints an approval URL but does not wait or authorize.

Complete the capture proof (PP-G3b + PP-G4 capture) in one interactive run:
```bash
PAYPAL_SMOKE_WAIT_APPROVAL=true node scripts/paypal-smoke/smoke-proof.mjs
```
The script prints an approval URL, then polls the new order. Open that URL, log in with the Sandbox Personal account, and approve the order while the script is waiting. It then authorizes, captures, and records the invoice payment.

Run a separate void proof (PP-G3b + PP-G4 void) the same way:
```bash
PAYPAL_SMOKE_WAIT_APPROVAL=true PAYPAL_SMOKE_FLOW=void node scripts/paypal-smoke/smoke-proof.mjs
```
Open and approve the newly printed link; the script then authorizes, voids, and cancels the invoice.

Optional tuning:
- `PAYPAL_SMOKE_APPROVAL_TIMEOUT_MS` (default `600000`)
- `PAYPAL_SMOKE_POLL_MS` (default `5000`)

## Security Invariants
- The script checks that `PAYPAL_API_BASE_URL` contains `sandbox.paypal.com` and fails immediately if run against production.
- Access tokens and secrets are redacted from console output.
