# PayPal Sandbox Smoke Proof Scripts (PP-G1 to PP-G3)

This directory contains standalone scripts to verify live PayPal Sandbox connectivity without requiring the backend database or full application stack.

## Milestones Covered
- **PP-G1**: Live OAuth2 `client_credentials` token retrieval with Sandbox API.
- **PP-G2**: Invoicing v2 draft creation with `termsHashSha256` embedded in `detail.note`, amount exact to the cent, and invoice dispatch via `/send`.
- **PP-G3**: Orders v2 creation with `intent: AUTHORIZE`, linked `invoice_id`, and subsequent authorization/capture readiness.

## Prerequisites
1. PayPal Developer Sandbox Account (Business & Personal): [https://developer.paypal.com](https://developer.paypal.com)
2. Sandbox App Client ID and Secret with **Invoicing** and **Orders** capabilities enabled.
3. Node.js 18+ (tested on Node v24).

## Running the Smoke Script
Set your credentials in your shell or `.env`:
```bash
export PAYPAL_CLIENT_ID="your_sandbox_client_id"
export PAYPAL_CLIENT_SECRET="your_sandbox_client_secret"
export PAYPAL_API_BASE_URL="https://api-m.sandbox.paypal.com" # Required Sandbox Host
```

Run:
```bash
node scripts/paypal-smoke/smoke-proof.mjs
```

## Security Invariants
- The script checks that `PAYPAL_API_BASE_URL` contains `sandbox.paypal.com` and fails immediately if run against production.
- Access tokens and secrets are redacted from console output.
