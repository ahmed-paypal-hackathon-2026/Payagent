import type {
  AgreementSnapshotDTO,
  DeliverableEvidenceDTO,
  MandateDTO,
  OfferDTO,
  SettlementStateDTO,
  StructuredTermsDTO,
} from "@/types/contracts";
import type { AuditEvent, DemoUser, MockDatabase, MockDeal } from "./types";

export const MOCK_USERS: DemoUser[] = [
  {
    id: "user-demo-buyer",
    email: "buyer.demo@example.test",
    displayName: "Northline Coffee",
    roleLabel: "Buyer",
  },
  {
    id: "user-demo-seller",
    email: "seller.demo@example.test",
    displayName: "Fieldwork Digital",
    roleLabel: "Seller",
  },
];

export const DEFAULT_DEMO_USER_ID = MOCK_USERS[0].id;

export function demoUserById(userId: string): DemoUser | null {
  return MOCK_USERS.find((user) => user.id === userId) ?? null;
}

const LANDING_TERMS: StructuredTermsDTO = {
  title: "Northline Coffee · Launch landing page",
  deliverables: [
    "Responsive landing page",
    "Editable design files",
    "Enquiry form",
  ],
  priceMinorUnits: 90000,
  currency: "USD",
  deliveryDays: 14,
  includedRevisions: 2,
  acceptanceCriteriaText:
    "The page follows the approved design on mobile and desktop. Test enquiries reach the client’s inbox.",
};

const AGREED_TERMS: StructuredTermsDTO = {
  ...LANDING_TERMS,
  priceMinorUnits: 98000,
  deliveryDays: 20,
};

const AGREEMENT_HASH =
  "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456";

const BUYER_MANDATE: MandateDTO = {
  mandateId: "mandate-demo-buyer-1",
  dealId: "deal-demo-001",
  partyRole: "BUYER",
  version: 1,
  currency: "USD",
  limitPriceMinorUnits: 100000,
  targetPriceMinorUnits: 90000,
  limitDeliveryDays: 21,
  targetDeliveryDays: 14,
  limitRevisions: 3,
  targetRevisions: 2,
  mustHaveDeliverables: ["Responsive landing page", "Enquiry form"],
  forbiddenTerms: ["Full brand rebrand"],
  mode: "ASSISTED",
  maxRounds: 5,
  expiresAt: "2026-11-20T21:59:00Z",
};

const SELLER_MANDATE: MandateDTO = {
  mandateId: "mandate-demo-seller-1",
  dealId: "deal-demo-001",
  partyRole: "SELLER",
  version: 1,
  currency: "USD",
  limitPriceMinorUnits: 95000,
  targetPriceMinorUnits: 115000,
  limitDeliveryDays: 25,
  targetDeliveryDays: 20,
  limitRevisions: 1,
  targetRevisions: 2,
  mustHaveDeliverables: ["Responsive landing page"],
  forbiddenTerms: ["Unlimited revisions", "Same-day delivery"],
  mode: "AUTONOMOUS_BOUNDED",
  maxRounds: 5,
  expiresAt: "2026-11-20T21:59:00Z",
};

function settlement(
  dealId: string,
  overrides: Partial<SettlementStateDTO> = {},
): SettlementStateDTO {
  return {
    dealId,
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
    amountMinorUnits: AGREED_TERMS.priceMinorUnits,
    currency: "USD",
    lastReconciledAt: null,
    ...overrides,
  };
}

function agreement(
  dealId: string,
  overrides: Partial<AgreementSnapshotDTO> = {},
): AgreementSnapshotDTO {
  return {
    agreementId: `agreement-${dealId}-v1`,
    dealId,
    version: 1,
    canonicalTerms: AGREED_TERMS,
    termsHashSha256: AGREEMENT_HASH,
    buyerAcceptedAt: null,
    sellerAcceptedAt: null,
    ...overrides,
  };
}

function offer(
  overrides: Partial<OfferDTO> & Pick<OfferDTO, "offerId" | "dealId" | "roundNumber" | "proposerRole">,
): OfferDTO {
  return {
    generatedByAgent: false,
    terms: LANDING_TERMS,
    rationaleSummary: "",
    callerUtilityScore: "DEMO_UNSCORED",
    mandateCompliant: true,
    createdAt: "2026-10-22T10:00:00Z",
    ...overrides,
  };
}

function audit(
  dealId: string,
  seq: number,
  type: string,
  actorRole: AuditEvent["actorRole"],
  summary: string,
  at: string,
  metadata?: AuditEvent["metadata"],
): AuditEvent {
  return { seq, dealId, type, actorRole, summary, at, metadata };
}

const AUTHORIZED_SETTLEMENT = settlement("deal-demo-002", {
  invoiceStatus: "SENT",
  paypalInvoiceId: "INV2-SYNTH-0001",
  paypalInvoiceNumber: "SYNTH-INV-001",
  paypalInvoiceViewUrl: "https://www.sandbox.paypal.com/invoice/p/#SYNTH-INV-001",
  paymentStatus: "AUTHORIZED",
  paypalOrderId: "ORD-SYNTH-0001",
  paypalAuthorizationId: "AUTH-SYNTH-0001",
  authorizationExpiresAt: "2026-11-20T12:00:00Z",
  lastReconciledAt: "2026-10-22T13:00:00Z",
});

const EVIDENCE: DeliverableEvidenceDTO = {
  evidenceId: "evidence-synth-001",
  dealId: "deal-demo-003",
  uploaderRole: "SELLER",
  filename: "Landing_Page_v1.zip",
  mimeType: "application/zip",
  sizeBytes: 204800,
  sha256Hash: "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
  version: 1,
  downloadSignedUrl:
    "https://api.localhost/api/v1/deals/deal-demo-003/evidence/evidence-synth-001/download?sig=SYNTH-HMAC",
  uploadedAt: "2026-10-22T14:00:00Z",
};

const BASE_OFFERS: OfferDTO[] = [
  offer({
    offerId: "offer-demo-001",
    dealId: "deal-demo-001",
    roundNumber: 1,
    proposerRole: "BUYER",
    terms: LANDING_TERMS,
    rationaleSummary: "Initial buyer brief converted into structured terms.",
    callerUtilityScore: "0.8500",
    createdAt: "2026-10-22T09:00:00Z",
  }),
  offer({
    offerId: "offer-synth-002",
    dealId: "deal-demo-001",
    roundNumber: 2,
    proposerRole: "SELLER",
    terms: {
      ...LANDING_TERMS,
      priceMinorUnits: 115000,
      deliveryDays: 22,
      includedRevisions: 1,
    },
    rationaleSummary:
      "Higher scope risk on tight timeline; single revision keeps delivery predictable.",
    callerUtilityScore: "0.6200",
    createdAt: "2026-10-22T10:00:00Z",
  }),
  offer({
    offerId: "offer-synth-003",
    dealId: "deal-demo-001",
    roundNumber: 3,
    proposerRole: "SELLER",
    generatedByAgent: true,
    terms: {
      ...LANDING_TERMS,
      priceMinorUnits: 125000,
      deliveryDays: 18,
    },
    rationaleSummary:
      "Synthetic over-budget probe for mandate enforcement tests (TST-01).",
    callerUtilityScore: "-1.0000",
    mandateCompliant: false,
    createdAt: "2026-10-22T10:15:00Z",
  }),
  offer({
    offerId: "offer-demo-004",
    dealId: "deal-demo-001",
    roundNumber: 4,
    proposerRole: "BUYER",
    terms: AGREED_TERMS,
    rationaleSummary: "Buyer moved to a deliverable-complete package at the limit.",
    callerUtilityScore: "0.7800",
    createdAt: "2026-10-22T11:30:00Z",
  }),
];

const NEGOTIATION_DEAL: MockDeal = {
  id: "deal-demo-001",
  buyerUserId: "user-demo-buyer",
  sellerUserId: "user-demo-seller",
  status: "TERMS_PENDING_ACCEPTANCE",
  stopReason: "NONE",
  terms: LANDING_TERMS,
  buyerMandate: BUYER_MANDATE,
  sellerMandate: SELLER_MANDATE,
  offers: BASE_OFFERS,
  currentAgreement: agreement("deal-demo-001", {
    buyerAcceptedAt: "2026-10-22T12:00:00Z",
    sellerAcceptedAt: null,
  }),
  settlement: settlement("deal-demo-001"),
  evidenceList: [],
  disputeStatus: "NONE",
  disputeReason: null,
  inviteToken: "demo-invite-deal-demo-001",
  createdAt: "2026-10-22T08:45:00Z",
  updatedAt: "2026-10-22T12:00:00Z",
  auditEvents: [
    audit("deal-demo-001", 1, "DEAL_CREATED", "BUYER", "Deal created from the buyer brief.", "2026-10-22T08:45:00Z"),
    audit("deal-demo-001", 2, "MANDATE_LOCKED", "BUYER", "Buyer locked mandate v1.", "2026-10-22T08:50:00Z", { version: 1 }),
    audit("deal-demo-001", 3, "OFFER_SUBMITTED", "BUYER", "Buyer submitted round 1 terms.", "2026-10-22T09:00:00Z", { round: 1, priceMinorUnits: 90000, currency: "USD" }),
    audit("deal-demo-001", 4, "MANDATE_LOCKED", "SELLER", "Seller locked mandate v1.", "2026-10-22T09:30:00Z", { version: 1 }),
    audit("deal-demo-001", 5, "OFFER_SUBMITTED", "SELLER", "Seller submitted round 2 terms.", "2026-10-22T10:00:00Z", { round: 2, priceMinorUnits: 115000, currency: "USD" }),
    audit("deal-demo-001", 6, "OFFER_SUBMITTED", "SELLER", "Bounded agent proposed round 3 terms.", "2026-10-22T10:15:00Z", { round: 3, priceMinorUnits: 125000, currency: "USD", generatedByAgent: true }),
    audit("deal-demo-001", 7, "OFFER_SUBMITTED", "BUYER", "Buyer submitted round 4 terms.", "2026-10-22T11:30:00Z", { round: 4, priceMinorUnits: 98000, currency: "USD" }),
    audit("deal-demo-001", 8, "AGREEMENT_PROPOSED", "BUYER", "Agreement v1 proposed for dual consent.", "2026-10-22T11:45:00Z", { version: 1 }),
    audit("deal-demo-001", 9, "AGREEMENT_ACCEPTED", "BUYER", "Buyer accepted agreement v1.", "2026-10-22T12:00:00Z", { version: 1 }),
  ],
};

const IN_PROGRESS_DEAL: MockDeal = {
  id: "deal-demo-002",
  buyerUserId: "user-demo-buyer",
  sellerUserId: "user-demo-seller",
  status: "IN_PROGRESS",
  stopReason: "CONVERGED",
  terms: LANDING_TERMS,
  buyerMandate: { ...BUYER_MANDATE, mandateId: "mandate-demo-buyer-2", dealId: "deal-demo-002" },
  sellerMandate: { ...SELLER_MANDATE, mandateId: "mandate-demo-seller-2", dealId: "deal-demo-002" },
  offers: BASE_OFFERS.map((item) => ({ ...item, dealId: "deal-demo-002" })),
  currentAgreement: agreement("deal-demo-002", {
    buyerAcceptedAt: "2026-10-22T12:00:00Z",
    sellerAcceptedAt: "2026-10-22T12:05:00Z",
  }),
  settlement: AUTHORIZED_SETTLEMENT,
  evidenceList: [],
  disputeStatus: "NONE",
  disputeReason: null,
  inviteToken: "demo-invite-deal-demo-002",
  createdAt: "2026-10-22T08:45:00Z",
  updatedAt: "2026-10-22T13:00:00Z",
  auditEvents: [
    audit("deal-demo-002", 1, "DEAL_CREATED", "BUYER", "Deal created from the buyer brief.", "2026-10-22T08:45:00Z"),
    audit("deal-demo-002", 2, "AGREEMENT_ACCEPTED", "BUYER", "Buyer accepted agreement v1.", "2026-10-22T12:00:00Z", { version: 1 }),
    audit("deal-demo-002", 3, "AGREEMENT_ACCEPTED", "SELLER", "Seller accepted agreement v1.", "2026-10-22T12:05:00Z", { version: 1 }),
    audit("deal-demo-002", 4, "INVOICE_SENT", "BUYER", "Sandbox invoice sent for the agreed amount.", "2026-10-22T12:30:00Z", { amountMinorUnits: 98000, currency: "USD" }),
    audit("deal-demo-002", 5, "PAYMENT_AUTHORIZED", "BUYER", "Buyer authorization hold created.", "2026-10-22T13:00:00Z", { amountMinorUnits: 98000, currency: "USD" }),
  ],
};

const DELIVERABLES_DEAL: MockDeal = {
  id: "deal-demo-003",
  buyerUserId: "user-demo-buyer",
  sellerUserId: "user-demo-seller",
  status: "DELIVERABLES_SUBMITTED",
  stopReason: "CONVERGED",
  terms: LANDING_TERMS,
  buyerMandate: { ...BUYER_MANDATE, mandateId: "mandate-demo-buyer-3", dealId: "deal-demo-003" },
  sellerMandate: { ...SELLER_MANDATE, mandateId: "mandate-demo-seller-3", dealId: "deal-demo-003" },
  offers: BASE_OFFERS.map((item) => ({ ...item, dealId: "deal-demo-003" })),
  currentAgreement: agreement("deal-demo-003", {
    buyerAcceptedAt: "2026-10-22T12:00:00Z",
    sellerAcceptedAt: "2026-10-22T12:05:00Z",
  }),
  settlement: { ...AUTHORIZED_SETTLEMENT, dealId: "deal-demo-003" },
  evidenceList: [EVIDENCE],
  disputeStatus: "NONE",
  disputeReason: null,
  inviteToken: "demo-invite-deal-demo-003",
  createdAt: "2026-10-22T08:45:00Z",
  updatedAt: "2026-10-22T14:00:00Z",
  auditEvents: [
    audit("deal-demo-003", 1, "DEAL_CREATED", "BUYER", "Deal created from the buyer brief.", "2026-10-22T08:45:00Z"),
    audit("deal-demo-003", 2, "PAYMENT_AUTHORIZED", "BUYER", "Buyer authorization hold created.", "2026-10-22T13:00:00Z", { amountMinorUnits: 98000, currency: "USD" }),
    audit("deal-demo-003", 3, "EVIDENCE_UPLOADED", "SELLER", "Seller uploaded deliverable evidence v1.", "2026-10-22T14:00:00Z", { version: 1, sizeBytes: 204800, mimeType: "application/zip" }),
  ],
};

const DISPUTED_DEAL: MockDeal = {
  id: "deal-synth-001",
  buyerUserId: "user-demo-buyer",
  sellerUserId: "user-demo-seller",
  status: "DISPUTED",
  stopReason: "USER_PAUSED",
  terms: LANDING_TERMS,
  buyerMandate: null,
  sellerMandate: null,
  offers: [],
  currentAgreement: null,
  settlement: {
    ...AUTHORIZED_SETTLEMENT,
    dealId: "deal-synth-001",
    lastReconciledAt: "2026-10-22T15:00:00Z",
  },
  evidenceList: [],
  disputeStatus: "OPEN",
  disputeReason: "Deliverable does not meet acceptance criteria (synthetic dispute fixture).",
  inviteToken: "demo-invite-deal-synth-001",
  createdAt: "2026-10-22T08:45:00Z",
  updatedAt: "2026-10-22T15:00:00Z",
  auditEvents: [
    audit("deal-synth-001", 1, "DEAL_CREATED", "BUYER", "Deal created from the buyer brief.", "2026-10-22T08:45:00Z"),
    audit("deal-synth-001", 2, "PAYMENT_AUTHORIZED", "BUYER", "Buyer authorization hold created.", "2026-10-22T13:00:00Z", { amountMinorUnits: 98000, currency: "USD" }),
    audit("deal-synth-001", 3, "DISPUTE_OPENED", "BUYER", "Buyer opened a dispute and capture was frozen.", "2026-10-22T15:00:00Z"),
  ],
};

function cloneSeed<T>(value: T): T {
  if (typeof structuredClone === "function") return structuredClone(value);
  return JSON.parse(JSON.stringify(value)) as T;
}

export function createSeedDatabase(): MockDatabase {
  return cloneSeed({
    schemaVersion: 1,
    deals: {
      "deal-demo-001": NEGOTIATION_DEAL,
      "deal-demo-002": IN_PROGRESS_DEAL,
      "deal-demo-003": DELIVERABLES_DEAL,
      "deal-synth-001": DISPUTED_DEAL,
    },
  });
}

export const DEMO_SCENARIO_ORDER = [
  "deal-demo-001",
  "deal-demo-002",
  "deal-demo-003",
  "deal-synth-001",
];
