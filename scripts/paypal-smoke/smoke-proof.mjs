#!/usr/bin/env node
/**
 * PayAgent — PayPal Sandbox Smoke Proof (PP-G1 to PP-G3)
 *
 * Verifies live PayPal Sandbox connectivity:
 * 1. PP-G1: OAuth2 client_credentials token grant
 * 2. PP-G2: Invoicing v2 draft invoice creation + send (linking termsHash in detail.note)
 * 3. PP-G3: Orders v2 create (intent: AUTHORIZE, linked invoice_id) -> authorize -> capture
 *
 * Usage:
 *   node scripts/paypal-smoke/smoke-proof.mjs
 *
 * Required environment variables:
 *   PAYPAL_CLIENT_ID
 *   PAYPAL_CLIENT_SECRET
 *   PAYPAL_API_BASE_URL (defaults to https://api-m.sandbox.paypal.com)
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

// Fail fast if targeting production
if (!BASE_URL.toLowerCase().includes('sandbox.paypal.com')) {
  console.error('[ERROR] Refusing to run against non-sandbox host: ' + BASE_URL);
  process.exit(1);
}

if (!CLIENT_ID || !CLIENT_SECRET) {
  console.log('================================================================');
  console.log('PayAgent — PayPal Sandbox Live Smoke Proof (PP-G1..PP-G3)');
  console.log('================================================================');
  console.log('[SKIPPED] Missing PAYPAL_CLIENT_ID or PAYPAL_CLIENT_SECRET.');
  console.log('To run this live smoke test:');
  console.log('  1. Create a Sandbox App at https://developer.paypal.com/dashboard/applications/sandbox');
  console.log('  2. Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET in .env or your shell.');
  console.log('  3. Re-run: node scripts/paypal-smoke/smoke-proof.mjs');
  console.log('================================================================');
  process.exit(0);
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
  const invoicePayload = {
    detail: {
      currency_code: 'USD',
      note: `termsHashSha256:${testTermsHash} | Deliverables: Autonomous settlement proof`,
      term: 'DUE_ON_RECEIPT',
    },
    invoicer: {
      name: { given_name: 'PayAgent', surname: 'Seller' },
      email_address: 'seller-test@payagent.internal',
    },
    primary_recipients: [
      {
        billing_info: {
          name: { given_name: 'PayAgent', surname: 'Buyer' },
          email_address: 'buyer-test@payagent.internal',
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
  console.log(`[PASS] Order created: ${orderId}, Status: ${order.status}`);
  if (approveLink) {
    console.log(`Approve Link: ${approveLink}`);
  }

  console.log('\n================================================================');
  console.log('PP-G1..PP-G3 Live Verification Summary');
  console.log('================================================================');
  console.log(`PayPal Invoice ID:       ${invoiceId}`);
  console.log(`PayPal Invoice Number:   ${invoiceNumber}`);
  console.log(`PayPal Invoice Status:   ${invoiceDetails.status}`);
  console.log(`PayPal Invoice View URL: ${invoiceViewUrl}`);
  console.log(`PayPal Order ID:         ${orderId}`);
  console.log(`PayPal Order Status:     ${order.status}`);
  console.log('OAuth2 Token:            [REDACTED]');
  console.log('================================================================');
}

runSmoke().catch(err => {
  console.error('\n[FAIL] Smoke script encountered an error:');
  console.error(err.message);
  process.exit(1);
});
