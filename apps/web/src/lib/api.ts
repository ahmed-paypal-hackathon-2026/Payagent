import type {
  DealWorkspaceDTO,
  OfferDTO,
  AgreementSnapshotDTO,
  MandateDTO,
  StructuredTermsDTO,
} from "@/types/contracts";

/**
 * Fixture-backed API client (NEXT_PUBLIC_USE_MOCKS=true).
 *
 * Serves Member F's canonical fixtures from shared/fixtures/ so the Deal Room
 * works end-to-end without a live backend. When mocks are disabled, swap this
 * module for the live REST client — the return types are the canonical
 * contracts from src/types/contracts.ts, so no parallel schema exists.
 */

const fixtureBuyerIntake: StructuredTermsDTO = {
  title: "Landing page redesign for Payagent demo",
  deliverables: [
    "Responsive landing page (Next.js)",
    "Brand-aligned Tailwind design system",
    "Contact form with validation",
  ],
  priceMinorUnits: 90000,
  currency: "USD",
  deliveryDays: 14,
  includedRevisions: 2,
  acceptanceCriteriaText:
    "Page matches approved wireframe; Lighthouse performance score >= 85 on mobile.",
};

const fixtureSellerCounteroffer: StructuredTermsDTO = {
  title: "Landing page redesign for Payagent demo",
  deliverables: [
    "Responsive landing page (Next.js)",
    "Brand-aligned Tailwind design system",
    "Contact form with validation",
  ],
  priceMinorUnits: 98000,
  currency: "USD",
  deliveryDays: 20,
  includedRevisions: 2,
  acceptanceCriteriaText:
    "Page matches approved wireframe; Lighthouse performance score >= 85 on mobile.",
};

const fixtureAgreement: AgreementSnapshotDTO = {
  agreementId: "agr-synth-001",
  dealId: "deal-synth-001",
  version: 1,
  canonicalTerms: {
    ...fixtureSellerCounteroffer,
    priceMinorUnits: 98000,
  },
  termsHashSha256:
    "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456",
  buyerAcceptedAt: "2026-10-22T12:00:00Z",
  sellerAcceptedAt: "2026-10-22T12:05:00Z",
};

const fixtureBuyerMandate: MandateDTO = {
  mandateId: "man-buyer-001",
  dealId: "deal-synth-001",
  partyRole: "BUYER",
  version: 1,
  currency: "USD",
  limitPriceMinorUnits: 120000,
  targetPriceMinorUnits: 90000,
  limitDeliveryDays: 30,
  targetDeliveryDays: 14,
  limitRevisions: 2,
  targetRevisions: 3,
  mustHaveDeliverables: [
    "Responsive landing page (Next.js)",
    "Brand-aligned Tailwind design system",
    "Contact form with validation",
  ],
  forbiddenTerms: ["resale rights"],
  mode: "ASSISTED",
  maxRounds: 5,
  expiresAt: "2026-12-31T23:59:59Z",
};

const fixtureOffers: OfferDTO[] = [
  {
    offerId: "off-001",
    dealId: "deal-synth-001",
    roundNumber: 1,
    proposerRole: "BUYER",
    generatedByAgent: false,
    terms: fixtureBuyerIntake,
    rationaleSummary: "Buyer opening offer at target price.",
    callerUtilityScore: "1.0000",
    mandateCompliant: true,
    createdAt: "2026-10-22T10:00:00Z",
  },
  {
    offerId: "off-002",
    dealId: "deal-synth-001",
    roundNumber: 2,
    proposerRole: "SELLER",
    generatedByAgent: false,
    terms: fixtureSellerCounteroffer,
    rationaleSummary:
      "Seller counteroffer: higher price and longer timeline to cover scope.",
    callerUtilityScore: "0.8750",
    mandateCompliant: true,
    createdAt: "2026-10-22T11:00:00Z",
  },
];

export const fixtureWorkspace: DealWorkspaceDTO = {
  dealId: "deal-synth-001",
  status: "AGREED",
  stopReason: "CONVERGED",
  buyerEmail: "buyer@payagent.demo",
  sellerEmail: "seller@payagent.demo",
  myRole: "BUYER",
  myMandate: fixtureBuyerMandate,
  offers: fixtureOffers,
  currentAgreement: fixtureAgreement,
  settlement: {
    dealId: "deal-synth-001",
    agreementVersion: 1,
    invoiceStatus: "NOT_CREATED",
    paypalInvoiceId: null,
    paypalInvoiceNumber: null,
    paypalInvoiceViewUrl: null,
    paymentStatus: "NOT_STARTED",
    paypalOrderId: null,
    paypalAuthorizationId: null,
    paypalCaptureId: null,
    authorizationExpiresAt: null,
    amountMinorUnits: 98000,
    currency: "USD",
    lastReconciledAt: null,
  },
  evidenceList: [],
  disputeStatus: "NONE",
  disputeReason: null,
};

import { getClientPublicConfig } from "./runtime-config";

/**
 * Loads the deal workspace. In mock mode this resolves fixture data after a
 * short delay to exercise loading states; against the live backend this
 * becomes `GET /api/v1/deals/{id}` with a Bearer token.
 */
export async function fetchWorkspace(
  dealId: string,
): Promise<DealWorkspaceDTO> {
  const { useMocks, apiBaseUrl } = getClientPublicConfig();
  if (!useMocks) {
    const res = await fetch(`${apiBaseUrl}/api/v1/deals/${dealId}`, {
      headers: { "Content-Type": "application/json" },
      credentials: "include",
    });
    if (!res.ok) {
      throw new Error(`Failed to load deal ${dealId}: ${res.status}`);
    }
    return (await res.json()) as DealWorkspaceDTO;
  }
  await new Promise((r) => setTimeout(r, 250));
  return { ...fixtureWorkspace, dealId };
}

/**
 * Accepts the current agreement version. In mock mode this flips the local
 * fixture state; against the live backend this becomes
 * `POST /api/v1/deals/{id}/acceptance` with `{agreementVersion, termsHashSha256}`.
 */
export async function acceptAgreement(
  dealId: string,
  agreementVersion: number,
  termsHashSha256: string,
  role: "BUYER" | "SELLER",
): Promise<AgreementSnapshotDTO> {
  const { useMocks, apiBaseUrl } = getClientPublicConfig();
  if (!useMocks) {
    const res = await fetch(`${apiBaseUrl}/api/v1/deals/${dealId}/acceptance`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": crypto.randomUUID(),
      },
      credentials: "include",
      body: JSON.stringify({ agreementVersion, termsHashSha256 }),
    });
    if (!res.ok) {
      throw new Error(`Acceptance failed: ${res.status}`);
    }
    return (await res.json()) as AgreementSnapshotDTO;
  }
  await new Promise((r) => setTimeout(r, 200));
  const now = new Date().toISOString();
  const key = role === "BUYER" ? "buyerAcceptedAt" : "sellerAcceptedAt";
  return {
    ...fixtureAgreement,
    [key]: now,
    buyerAcceptedAt: role === "BUYER" ? now : fixtureAgreement.buyerAcceptedAt,
    sellerAcceptedAt: role === "SELLER" ? now : fixtureAgreement.sellerAcceptedAt,
  };
}

/** Formats minor units for display, e.g. 98000 -> "$980.00". */
export function formatMinorUnits(
  minor: number,
  currency: string,
): string {
  const value = minor / 100;
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

/** Computes a per-attribute diff between two term sets for the red/green view. */
export type TermDiff = {
  label: string;
  from: string;
  to: string;
  improved: boolean;
};

export function diffTerms(
  a: StructuredTermsDTO,
  b: StructuredTermsDTO,
): TermDiff[] {
  const fmt = (v: number) => v.toLocaleString("en-US");
  return [
    {
      label: "Price",
      from: formatMinorUnits(a.priceMinorUnits, a.currency),
      to: formatMinorUnits(b.priceMinorUnits, b.currency),
      improved: b.priceMinorUnits <= a.priceMinorUnits,
    },
    {
      label: "Delivery days",
      from: `${fmt(a.deliveryDays)} days`,
      to: `${fmt(b.deliveryDays)} days`,
      improved: b.deliveryDays <= a.deliveryDays,
    },
    {
      label: "Included revisions",
      from: fmt(a.includedRevisions),
      to: fmt(b.includedRevisions),
      improved: b.includedRevisions >= a.includedRevisions,
    },
    {
      label: "Deliverables",
      from: `${a.deliverables.length} items`,
      to: `${b.deliverables.length} items`,
      improved: b.deliverables.length >= a.deliverables.length,
    },
  ];
}
