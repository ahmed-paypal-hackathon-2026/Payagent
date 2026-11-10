#!/usr/bin/env node
/**
 * PayAgent - PayPal Sandbox Smoke Proof (PP-G1 to PP-G4)
 *
 * Verifies live PayPal Sandbox connectivity:
 * 1. PP-G1: OAuth2 client_credentials token grant
 * 2. PP-G2: Invoicing v2 draft invoice creation + send (linking termsHash in detail.note)
 * 3. PP-G3: Orders v2 create (intent: AUTHORIZE, linked invoice_id) -> buyer approval -> authorize
 * 4. PP-G4: Capture the authorization (default) or void it (`PAYPAL_SMOKE_FLOW=void`)
 *
 * Buyer approval is manual (Sandbox login). Set PAYPAL_SMOKE_WAIT_APPROVAL=true
 * to poll the order until it is approved, then authorize/capture (or void).
 *
 * Usage:
 *   node scripts/paypal-smoke/smoke-proof.mjs
 *   PAYPAL_SMOKE_WAIT_APPROVAL=true node scripts/paypal-smoke/smoke-proof.mjs
 *   PAYPAL_SMOKE_WAIT_APPROVAL=true PAYPAL_SMOKE_FLOW=void node scripts/paypal-smoke/smoke-proof.mjs
 *
 * Required environment variables:
 *   PAYPAL_CLIENT_ID
 *   PAYPAL_CLIENT_SECRET
 *   PAYPAL_API_BASE_URL (defaults to https://api-m.sandbox.paypal.com)
 *
 * Optional environment variables:
 *   PAYPAL_INVOICER_EMAIL  (Sandbox Business account email)
 *   PAYPAL_PAYER_EMAIL     (Sandbox Personal account email)
 *   PAYPAL_SMOKE_WAIT_APPROVAL  (true to wait for manual approval)
 *   PAYPAL_SMOKE_FLOW      (capture | void, defaults to capture)
 *   PAYPAL_SMOKE_APPROVAL_TIMEOUT_MS (defaults to 600000)
 *   PAYPAL_SMOKE_POLL_MS   (defaults to 5000)
 */

import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// Optional: load .env if present
const envPath = resolve(process.cwd(), '.env');
if (existsSync(envPath)) {
  const content = readFileSync(envPath, 'utf8');
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx > 0) {
      const key = trimmed.slice(0, eqIdx).trim();
      const val = trimmed.slice(eqIdx + 1).trim();
      if (!process.env[key]) {
        process.env[key] = val;
      }
    }
  }
}

const BASE_URL = process.env.PAYPAL_API_BASE_URL || 'https://api-m.sandbox.paypal.com';
const CLIENT_ID = process.env.PAYPAL_CLIENT_ID;
const CLIENT_SECRET = process.env.PAYPAL_CLIENT_SECRET;
const WAIT_FOR_APPROVAL = /^(1|true|yes)$/i.test(process.env.PAYPAL_SMOKE_WAIT_APPROVAL || '');
const FLOW = (process.env.PAYPAL_SMOKE_FLOW || 'capture').toLowerCase();
const APPROVAL_TIMEOUT_MS = Number(process.env.PAYPAL_SMOKE_APPROVAL_TIMEOUT_MS || 600000);
const POLL_INTERVAL_MS = Number(process.env.PAYPAL_SMOKE_POLL_MS || 5000);

if (!['capture', 'void'].includes(FLOW)) {
  console.error('[ERROR] PAYPAL_SMOKE_FLOW must be "capture" or "void", got: ' + FLOW);
  process.exit(1);
}

// Fail fast if targeting production
if (!BASE_URL.toLowerCase().includes('sandbox.paypal.com')) {
  console.error('[ERROR] Refusing to run against non-sandbox host: ' + BASE_URL);
  process.exit(1);
}

if (!CLIENT_ID || !CLIENT_SECRET) {
  console.log('================================================================');
  console.log('PayAgent - PayPal Sandbox Live Smoke Proof (PP-G1..PP-G4)');
  console.log('================================================================');
  console.log('[SKIPPED] Missing PAYPAL_CLIENT_ID or PAYPAL_CLIENT_SECRET.');
  console.log('To run this live smoke test:');
  console.log('  1. Create a Sandbox App at https://developer.paypal.com/dashboard/applications/sandbox');
  console.log('  2. Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET in .env or your shell.');
  console.log('  3. Re-run: node scripts/paypal-smoke/smoke-proof.mjs');
  console.log('================================================================');
  process.exit(0);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function request(path, options = {}) {
  const url = `${BASE_URL}${path}`;
  const res = await fetch(url, options);
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    // raw text
  }
  if (!res.ok) {
    const errorMsg = json?.message || json?.error_description || text || res.statusText;
    throw new Error(`HTTP ${res.status} ${res.statusText} on ${path}: ${errorMsg}`);
  }
  return json;
}

async function runSmoke() {
  console.log('================================================================');
  console.log('PayAgent — PayPal Sandbox Live Smoke Proof');
  console.log(`Target: ${BASE_URL}`);
  console.log('================================================================\n');

  // 1. PP-G1: OAuth2 Token
  console.log('[1/4] PP-G1: Requesting OAuth2 token (client_credentials)...');
  const basicAuth = Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString('base64');
  const tokenResp = await request('/v1/oauth2/token', {
    method: 'POST',
    headers: {
      'Authorization': `Basic ${basicAuth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
  });

  const accessToken = tokenResp.access_token;
  console.log(`[PASS] PP-G1: Token obtained. Expires in: ${tokenResp.expires_in}s. TokenType: ${tokenResp.token_type}`);

  const bearerHeaders = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json',
  };

  // 2. PP-G2: Invoicing v2 Create + Send
  console.log('\n[2/4] PP-G2: Creating draft invoice (Invoicing v2)...');
  const testTermsHash = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855';
  const invoicerEmail = process.env.PAYPAL_INVOICER_EMAIL || 'seller-test@payagent.internal';
  const payerEmail = process.env.PAYPAL_PAYER_EMAIL || 'buyer-test@payagent.internal';
  if (!process.env.PAYPAL_INVOICER_EMAIL || !process.env.PAYPAL_PAYER_EMAIL) {
    console.log('[WARN] PAYPAL_INVOICER_EMAIL / PAYPAL_PAYER_EMAIL not set. Using placeholder sandbox emails.');
    console.log('       Set them to your Sandbox Business/Personal account emails for a reliable /send.');
  }
  const invoicePayload = {
    detail: {
      currency_code: 'USD',
      note: `termsHashSha256:${testTermsHash} | Deliverables: Autonomous settlement proof`,
      term: 'DUE_ON_RECEIPT',
    },
    invoicer: {
      name: { given_name: 'PayAgent', surname: 'Seller' },
      email_address: invoicerEmail,
    },
    primary_recipients: [
      {
        billing_info: {
          name: { given_name: 'PayAgent', surname: 'Buyer' },
          email_address: payerEmail,
        },
      },
    ],
    items: [
      {
        name: 'AI Agent Negotiation Agreement Settlement',
        description: 'Settlement for terms hash ' + testTermsHash.slice(0, 16) + '...',
        quantity: '1',
        unit_amount: {
          currency_code: 'USD',
          value: '980.00',
        },
      },
    ],
    amount: {
      currency_code: 'USD',
      value: '980.00',
      breakdown: {
        item_total: {
          currency_code: 'USD',
          value: '980.00',
        },
      },
    },
  };

  const invoice = await request('/v2/invoicing/invoices', {
    method: 'POST',
    headers: {
      ...bearerHeaders,
      'PayPal-Request-Id': `smoke-inv-${Date.now()}`,
    },
    body: JSON.stringify(invoicePayload),
  });

  const invoiceId = invoice.id;
  const invoiceNumber = invoice.detail?.invoice_number || 'N/A';
  console.log(`[PASS] Invoice created: ${invoiceId} (Number: ${invoiceNumber})`);

  console.log(`[2b/4] Sending invoice ${invoiceId}...`);
  await request(`/v2/invoicing/invoices/${invoiceId}/send`, {
    method: 'POST',
    headers: {
      ...bearerHeaders,
      'PayPal-Request-Id': `smoke-send-${Date.now()}`,
    },
    body: JSON.stringify({ send_to_recipient: true }),
  });

  const invoiceDetails = await request(`/v2/invoicing/invoices/${invoiceId}`, {
    method: 'GET',
    headers: bearerHeaders,
  });

  const invoiceViewUrl = invoiceDetails.detail?.metadata?.payer_view_url ||
    invoiceDetails.links?.find(l => l.rel === 'payer-view')?.href ||
    `https://www.sandbox.paypal.com/invoice/payerView/details/${invoiceId}`;

  console.log(`[PASS] PP-G2: Invoice sent. Status: ${invoiceDetails.status}. View URL: ${invoiceViewUrl}`);

  // 3. PP-G3: Orders v2 create with intent: AUTHORIZE
  console.log('\n[3/4] PP-G3: Creating Order v2 (intent: AUTHORIZE, linked invoice)...');
  const orderPayload = {
    intent: 'AUTHORIZE',
    purchase_units: [
      {
        reference_id: `deal-smoke-${Date.now()}`,
        invoice_id: invoiceId,
        description: 'PayAgent autonomous negotiation settlement hold',
        amount: {
          currency_code: 'USD',
          value: '980.00',
        },
      },
    ],
  };

  const order = await request('/v2/checkout/orders', {
    method: 'POST',
    headers: {
      ...bearerHeaders,
      'PayPal-Request-Id': `smoke-ord-${Date.now()}`,
    },
    body: JSON.stringify(orderPayload),
  });

  const orderId = order.id;
  const approveLink = order.links?.find(l => l.rel === 'approve')?.href;
  console.log(`[PASS] PP-G3a: Order created: ${orderId}, Status: ${order.status}`);
  if (approveLink) {
    console.log(`Approve Link: ${approveLink}`);
  }

  let orderStatus = order.status;
  let authorizationId = null;
  let captureId = null;
  let voided = false;

  if (!WAIT_FOR_APPROVAL) {
    console.log('\n[SKIP] PP-G3b/PP-G4: Buyer approval not requested.');
    console.log('       To complete PP-G3b/PP-G4, re-run with:');
    console.log('       PAYPAL_SMOKE_WAIT_APPROVAL=true node scripts/paypal-smoke/smoke-proof.mjs');
    console.log('       Approve the new link while the script waits, then it authorizes and captures.');
    console.log('       Use PAYPAL_SMOKE_FLOW=void on a separate run to prove authorization void.');
  } else {
    console.log('\n[3b/4] PP-G3: Waiting for buyer approval in the Sandbox browser...');
    const deadline = Date.now() + APPROVAL_TIMEOUT_MS;
    while (Date.now() < deadline) {
      const current = await request(`/v2/checkout/orders/${orderId}`, {
        method: 'GET',
        headers: bearerHeaders,
      });
      orderStatus = current.status;
      if (orderStatus === 'APPROVED' || orderStatus === 'COMPLETED') {
        break;
      }
      console.log(`  order status: ${orderStatus}; retrying in ${Math.round(POLL_INTERVAL_MS / 1000)}s`);
      await sleep(POLL_INTERVAL_MS);
    }
    if (orderStatus !== 'APPROVED' && orderStatus !== 'COMPLETED') {
      throw new Error(`Order ${orderId} was not approved within ${APPROVAL_TIMEOUT_MS}ms (last status: ${orderStatus})`);
    }

    const authResponse = await request(`/v2/checkout/orders/${orderId}/authorize`, {
      method: 'POST',
      headers: {
        ...bearerHeaders,
        'PayPal-Request-Id': `smoke-auth-${Date.now()}`,
      },
      body: JSON.stringify({}),
    });
    const authorization = authResponse.purchase_units?.[0]?.payments?.authorizations?.[0];
    authorizationId = authorization?.id;
    if (!authorizationId) {
      throw new Error('Order authorization response did not include an authorization ID');
    }
    console.log(`[PASS] PP-G3: Order authorized. AUTH=${authorizationId}, status=${authorization.status}`);

    console.log(`\n[4/4] PP-G4: ${FLOW === 'capture' ? 'Capturing authorization' : 'Voiding authorization'} ${authorizationId}...`);
    if (FLOW === 'capture') {
      const capture = await request(`/v2/payments/authorizations/${authorizationId}/capture`, {
        method: 'POST',
        headers: {
          ...bearerHeaders,
          'PayPal-Request-Id': `smoke-cap-${Date.now()}`,
        },
        body: JSON.stringify({
          final_capture: true,
          amount: {
            currency_code: 'USD',
            value: '980.00',
          },
        }),
      });
      captureId = capture.id;
      if (capture.status !== 'COMPLETED' && capture.status !== 'PENDING') {
        throw new Error(`Unexpected capture status: ${capture.status}`);
      }

      try {
        await request(`/v2/invoicing/invoices/${invoiceId}/payments`, {
          method: 'POST',
          headers: {
            ...bearerHeaders,
            'PayPal-Request-Id': `smoke-invpay-${Date.now()}`,
          },
          body: JSON.stringify({
            method: 'PAYPAL',
            payment_date: new Date().toISOString().slice(0, 10),
            amount: {
              currency_code: 'USD',
              value: '980.00',
            },
            note: 'PayAgent smoke proof capture ' + captureId,
          }),
        });
        console.log('[PASS] Invoice payment recorded.');
      } catch (err) {
        console.log(`[WARN] Capture succeeded but invoice payment recording failed: ${err.message}`);
      }

      console.log(`[PASS] PP-G4: CAP=${captureId}, status=${capture.status}`);
    } else {
      await request(`/v2/payments/authorizations/${authorizationId}/void`, {
        method: 'POST',
        headers: {
          ...bearerHeaders,
          'PayPal-Request-Id': `smoke-void-${Date.now()}`,
        },
        body: JSON.stringify({}),
      });
      voided = true;

      try {
        await request(`/v2/invoicing/invoices/${invoiceId}/cancel`, {
          method: 'POST',
          headers: {
            ...bearerHeaders,
            'PayPal-Request-Id': `smoke-inv-cancel-${Date.now()}`,
          },
          body: JSON.stringify({
            subject: 'PayAgent smoke proof cancellation',
            note: 'Authorization voided by sandbox smoke proof',
            send_to_invoicer: false,
            send_to_recipient: false,
          }),
        });
        console.log('[PASS] Invoice cancelled.');
      } catch (err) {
        console.log(`[WARN] Authorization voided but invoice cancellation failed: ${err.message}`);
      }

      console.log(`[PASS] PP-G4: AUTH ${authorizationId} voided.`);
    }
  }

  console.log('\n================================================================');
  console.log('PP-G1..PP-G4 Live Verification Summary');
  console.log('================================================================');
  console.log(`PayPal Invoice ID:       ${invoiceId}`);
  console.log(`PayPal Invoice Number:   ${invoiceNumber}`);
  console.log(`PayPal Invoice Status:   ${invoiceDetails.status}`);
  console.log(`PayPal Invoice View URL: ${invoiceViewUrl}`);
  console.log(`PayPal Order ID:         ${orderId}`);
  console.log(`PayPal Order Status:     ${orderStatus}`);
  console.log(`PP-G4 Flow:              ${WAIT_FOR_APPROVAL ? FLOW : 'not run (approval required)'}`);
  console.log(`PayPal Authorization ID: ${authorizationId || 'not created'}`);
  console.log(`PayPal Capture ID:       ${captureId || 'not created'}`);
  console.log(`Authorization Voided:    ${voided}`);
  console.log('OAuth2 Token:            [REDACTED]');
  console.log('================================================================');
}

runSmoke().catch(err => {
  console.error('\n[FAIL] Smoke script encountered an error:');
  console.error(err.message);
  process.exit(1);
});
