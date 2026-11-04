# PayPal Sandbox Settlement Integration Guide

**Owner:** Member B (PayPal Adapter & Settlement Lifecycle)  
**Date:** 2026-10-25  
**Version:** 1.0.0  
**Stack:** Spring Boot 3.3, Java 21, PayPal REST API (Invoicing v2, Orders v2, Payments v2, Webhooks v1)  

---

## 1. Architecture Overview: Dual-Rail Settlement

PayAgent enforces a dual-rail settlement model designed for autonomous and assisted bilateral contract settlement:

```
[ AGREED Deal ]
       │
       ▼ (1) POST /deals/{id}/settlement/initiate
┌────────────────────────────────────────────────────────┐
│                      Dual Rails                        │
├───────────────────────────┬────────────────────────────┤
│ Rail 1: Commercial Record │ Rail 2: Escrow Funds Hold   │
│ (Invoicing v2)            │ (Orders v2 / Payments v2)   │
├───────────────────────────┼────────────────────────────┤
│ 1. Create Draft Invoice   │ 1. Create Order             │
│    (note=termsHashSha256) │    (intent: AUTHORIZE)     │
│ 2. Send Invoice (/send)   │ 2. Linked invoice_id       │
│    Status: SENT           │    Status: ORDER_CREATED   │
└───────────────────────────┴────────────────────────────┘
       │
       ▼ (2) Buyer Approves Order & POST /deals/{id}/settlement/authorize
┌────────────────────────────────────────────────────────┐
│ Authorize Hold:                                        │
│ Calls POST /v2/checkout/orders/{id}/authorize          │
│ Stores paypalAuthorizationId (AUTH-...)                │
│ Deal Status → IN_PROGRESS                              │
│ Payment Status → AUTHORIZED                            │
└────────────────────────────────────────────────────────┘
       │
       ▼ (3) Seller Submits Deliverable (Member E) → DELIVERABLES_SUBMITTED
       │
       ├─────────────────────────────────────────┐
       ▼ [Buyer Accepts]                         ▼ [Dispute Opened / Cancel]
┌─────────────────────────────────────┐   ┌───────────────────────────────────┐
│ POST /deals/{id}/accept-deliverables│   │ Deal Status → DISPUTED (Freeze)   │
│ 1. Re-check: Deal NOT DISPUTED     │   │ Automated capture BLOCKED (409)   │
│ 2. Capture: AUTH-... → CAP-...     │   │                                   │
│ 3. Record payment on Invoice       │   │ On Void / Mutual Cancel:          │
│ 4. Payment → COMPLETED              │   │ POST /deals/{id}/settlement/void  │
│ 5. Invoice → PAID                   │   │ Auth → VOIDED, Invoice → CANCELLED│
│ 6. Deal → ACCEPTED → CLOSED         │   └───────────────────────────────────┘
└─────────────────────────────────────┘
```

---

## 2. Configuration & Sandbox Setup

### 2.1 Required Environment Variables

All secrets must come from the environment (e.g. `.env` locally, or platform secrets). Never commit credentials.

| Variable | Description | Required | Example |
|----------|-------------|----------|---------|
| `PAYPAL_API_BASE_URL` | Base URL for PayPal REST API | Yes | `https://api-m.sandbox.paypal.com` |
| `PAYPAL_CLIENT_ID` | Sandbox App Client ID | Yes | `AXXX...` |
| `PAYPAL_CLIENT_SECRET` | Sandbox App Secret | Yes | `EYYY...` |
| `PAYPAL_WEBHOOK_ID` | Registered Webhook ID | Yes | `WH-999...` |

### 2.2 Sandbox Account Configuration Steps
1. Navigate to the [PayPal Developer Dashboard](https://developer.paypal.com/dashboard/applications/sandbox).
2. Log in and go to **Testing Tools → Sandbox Accounts**:
   - Create a **Business Account** (acts as Seller/Invoicer).
   - Create a **Personal Account** (acts as Buyer).
3. Under **Apps & Credentials → Sandbox**, create a REST API App associated with the Business Account.
4. Copy the `Client ID` and `Secret` to your local `.env`.
5. Under App Settings, ensure features are enabled:
   - **Invoicing**
   - **Customer Checkout (Orders)**
   - **Webhooks**

### 2.3 Strict Sandbox Guardrail
Both `PayPalAuthClient` and `PayPalEnvironmentGuard` check that `PAYPAL_API_BASE_URL` targets `sandbox.paypal.com`. Any attempt to connect to production hosts (`api-m.paypal.com`) causes immediate startup or request failure.

---

## 3. Endpoints Implemented by Member B

| Method | Path | Required Headers | Payload | Description |
|--------|------|------------------|---------|-------------|
| `POST` | `/api/v1/deals/{id}/settlement/initiate` | `Idempotency-Key` | None | Initiates dual-rail settlement. Requires `DealStatus == AGREED`. Creates & sends invoice; creates `AUTHORIZE` order. Returns `SettlementStateDTO`. |
| `POST` | `/api/v1/deals/{id}/settlement/authorize` | `Idempotency-Key` | `{"paypalOrderId":"..."}` | Authorizes approved PayPal order. Transitions deal to `IN_PROGRESS`. Returns `SettlementStateDTO`. |
| `POST` | `/api/v1/deals/{id}/accept-deliverables` | `Idempotency-Key` | `{"evidenceVersion": 1}` | **BUYER only**. Re-verifies deal is NOT `DISPUTED`. Captures authorization hold, records payment on invoice. Transitions deal to `ACCEPTED` and `CLOSED`. Returns `SettlementStateDTO`. |
| `POST` | `/api/v1/deals/{id}/settlement/void` | `Idempotency-Key` (opt) | `{"reason":"..."}` | Voids active authorization hold and cancels draft/sent invoice. Returns `SettlementStateDTO`. |
| `GET` | `/api/v1/deals/{id}/settlement` | None | None | Fetches current settlement status (`SettlementStateDTO`). |
| `POST` | `/api/v1/paypal/webhooks` | `PAYPAL-*` signature headers | Raw PayPal Event JSON | Verifies signature via PayPal; deduplicates by `event_id`; reconciles invoice and payment state. |

---

## 4. Contract Status Mapping

### 4.1 Invoice Status Mapping
| PayPal Invoicing v2 Status | Canonical `InvoiceLifecycleStatus` |
|----------------------------|-----------------------------------|
| `DRAFT` | `DRAFT` |
| `SENT`, `SCHEDULED` | `SENT` |
| `PAID`, `MARKED_AS_PAID` | `PAID` |
| `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED` | `CANCELLED` |
| Other / Unrecognized | `UNKNOWN` |

### 4.2 Payment Status Mapping
| PayPal Orders/Payments v2 Status | Canonical `PaymentLifecycleStatus` |
|-----------------------------------|-----------------------------------|
| `CREATED`, `SAVED`, `APPROVED` | `ORDER_CREATED` |
| `CREATED`, `PENDING` (Authorization) | `AUTHORIZED` |
| `CAPTURED`, `COMPLETED` (Capture) | `COMPLETED` |
| `VOIDED` | `VOIDED` |
| `DENIED`, `FAILED` | `DECLINED` |
| Ambiguous / Other | `UNKNOWN` |

---

## 5. Security & Invariant Enforcements

1. **Exact-Cent Minor Units:** Amounts are stored and manipulated internally as integer minor units (`long`, cents). Conversion to decimal strings (e.g. `98000` → `"980.00"`) occurs strictly at the PayPal boundary in `PayPalMoneyConverter` using `BigDecimal`. Floating-point arithmetic is forbidden.
2. **RFC-8785 Agreement Hash Immutability:** The RFC-8785 terms hash (`termsHashSha256`) and deliverables summary are embedded directly into `detail.note` of the PayPal invoice during creation (since PayPal discards notes passed to the `/send` endpoint).
3. **Dispute Freeze:** When a deal enters `DISPUTED` status, `SettlementService.acceptDeliverables()` blocks execution and returns `409 Conflict`. No automated capture can proceed during a dispute.
4. **Idempotency & Double-Capture Guard:**
   - Every mutating settlement endpoint enforces `Idempotency-Key`.
   - Results are cached in `idempotency_keys` with a SHA-256 hash of the request.
   - Concurrent calls and double-clicks are prevented by in-memory concurrency locks and state checks (`PaymentLifecycleStatus == COMPLETED` returns stored state without secondary PayPal API calls).
5. **Webhook Signature Verification & Deduplication:**
   - All incoming webhooks must pass cryptographic verification via `POST /v1/notifications/verify-webhook-signature`. Tampered requests return `400 Bad Request`.
   - Events are deduplicated by primary key `event_id` in `paypal_events`. Duplicates return `200 OK` with no duplicate side effects.

---

## 6. Interpretation of Release Gates (PP-G1 to PP-G5)

| Gate | Name | Interpretation & Acceptance Target |
|------|------|-----------------------------------|
| **PP-G1** | OAuth2 Token Exchange | Live Sandbox OAuth token retrieved with `grant_type=client_credentials`, cached with TTL, proactive 60s refresh margin. Verified by `smoke-proof.mjs` and `PayPalAuthClientTest`. |
| **PP-G2** | Invoicing v2 Life Cycle | Invoicing v2 draft created with `termsHashSha256` in `detail.note`, matching agreement price to the exact cent, and successfully sent (`SENT` status, real `INV2-...` ID). Verified by `smoke-proof.mjs` and `PayPalInvoiceServiceTest`. |
| **PP-G3** | Orders v2 Authorization Hold | Orders v2 order created with `intent: AUTHORIZE`, linked `invoice_id`, approved and authorized into an `AUTH-...` hold. Verified by `smoke-proof.mjs` and `PayPalOrderServiceTest`. |
| **PP-G4** | Settlement Capture & Void | Buyer accepts deliverables → captured via Payments v2 (`CAP-...`), payment recorded on invoice (`PAID`), deal transitions to `ACCEPTED`/`CLOSED`. On dispute or mutual cancel, authorization is voided. Verified by `SettlementServiceTest`. |
| **PP-G5** | Webhook Verification & Dedupe | Webhooks signed with PayPal PKI verified via `verify-webhook-signature`; tampered webhooks rejected with `400`; duplicate events deduplicated via `paypal_events` table; state reconciled. Verified by `PayPalWebhookControllerTest` and `SettlementReconciliationServiceTest`. |

---

## 7. How to Run Smoke Scripts and Tests

### 7.1 Standalone Smoke Script
```bash
# Provide credentials
export PAYPAL_CLIENT_ID="your_sandbox_client_id"
export PAYPAL_CLIENT_SECRET="your_sandbox_client_secret"
export PAYPAL_API_BASE_URL="https://api-m.sandbox.paypal.com"

# Run smoke test
node scripts/paypal-smoke/smoke-proof.mjs
```

### 7.2 Backend Unit & Integration Tests
```bash
cd services/api
./mvnw -B test -Dtest="com.payagent.paypal.*Test,com.payagent.settlement.*Test"
```

---

## 8. Cross-Team Requests

### Member A (Domain & Security Owner)
1. **SecurityConfig Permitted Webhook Endpoint:**  
   In `SecurityConfig.java`, permit anonymous access to the PayPal webhook receiver:
   ```java
   .requestMatchers("/api/v1/paypal/webhooks").permitAll()
   ```
2. **DealPort Integration:**  
   When Member A's `DealService` is ready, implement `com.payagent.settlement.port.DealPort` to provide deal state transitions, agreement retrieval, and buyer/seller validation. (A default adapter `DefaultDealPortAdapter` currently provides baseline JDBC-backed functionality).

### Member D (Web Workspace Owner)
1. **Settlement Initiation Button:** In the deal view (when `DealStatus == AGREED`), call `POST /api/v1/deals/{id}/settlement/initiate` with header `Idempotency-Key: <uuid>`.
2. **Buyer Approval Flow:** Redirect or popup PayPal Checkout using `links.find(l => l.rel === 'approve')` from order creation, then call `POST /api/v1/deals/{id}/settlement/authorize` with body `{"paypalOrderId": "<orderId>"}`.
3. **Accept Deliverables Button:** When `DealStatus == DELIVERABLES_SUBMITTED`, render "Accept & Release Payment" button for the BUYER calling `POST /api/v1/deals/{id}/accept-deliverables` with header `Idempotency-Key: <uuid>`.

### Member E (Infra & Realtime Owner)
1. **Public Staging Webhook Tunnel:**  
   Provide a public HTTPS endpoint (via ngrok, Cloudflare Tunnel, or Render URL) pointing to `/api/v1/paypal/webhooks` for Sandbox webhook delivery, and configure `PAYPAL_WEBHOOK_ID` in production/staging environment variables.

### Member F (Fixtures & Release Gates Owner)
1. **Settlement Fixtures:** Add canonical sample fixtures for `SettlementStateDTO` and PayPal webhook event payloads (`PAYMENT.CAPTURE.COMPLETED`, `INVOICING.INVOICE.PAID`) into `shared/fixtures/`.
