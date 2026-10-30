import type {
  AgreementSnapshotDTO,
  DealWorkspaceDTO,
  DeliverableEvidenceDTO,
  MandateDTO,
  OfferDTO,
  PartyRole,
  SettlementStateDTO,
  StructuredTermsDTO,
} from "@/types/contracts";
import { demoUserById } from "./seed";
import type {
  IntakeInput,
  IntakeResult,
  MandateInput,
  MockDatabase,
  MockDeal,
  MockDealSummary,
} from "./types";

export class MockActionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "MockActionError";
  }
}

const MAX_EVIDENCE_BYTES = 10 * 1024 * 1024;
const ALLOWED_EVIDENCE_TYPES = new Set([
  "application/pdf",
  "application/zip",
  "image/png",
  "image/jpeg",
  "text/plain",
]);

function fail(message: string): never {
  throw new MockActionError(message);
}

function newId(prefix: string): string {
  const random = globalThis.crypto?.randomUUID?.();
  if (random) return `${prefix}-${random}`;
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function nowIso(): string {
  return new Date().toISOString();
}

function roleFor(deal: MockDeal, userId: string): PartyRole | null {
  if (deal.buyerUserId === userId) return "BUYER";
  if (deal.sellerUserId === userId) return "SELLER";
  return null;
}

function requireDeal(db: MockDatabase, dealId: string): MockDeal {
  const deal = db.deals[dealId];
  if (!deal) fail("This deal does not exist in the demo dataset.");
  return deal;
}

function requireRole(deal: MockDeal, userId: string): PartyRole {
  const role = roleFor(deal, userId);
  if (!role) fail("You are not a participant in this deal.");
  return role;
}

function assertNotFrozen(deal: MockDeal): void {
  if (deal.status === "DISPUTED" || deal.disputeStatus === "OPEN") {
    fail("This deal is frozen by an open dispute. Automated capture and acceptance are blocked.");
  }
  if (deal.status === "CLOSED" || deal.status === "CANCELLED") {
    fail("This deal is closed.");
  }
}

function assertMutableNegotiation(deal: MockDeal): void {
  assertNotFrozen(deal);
  if (deal.status !== "ACTIVE_NEGOTIATION" && deal.status !== "TERMS_PENDING_ACCEPTANCE") {
    fail(`Negotiation changes are not available while the deal is ${deal.status}.`);
  }
}

function pushAudit(
  deal: MockDeal,
  type: string,
  actorRole: PartyRole | null,
  summary: string,
  metadata?: Record<string, string | number | boolean | null>,
): void {
  const seq = deal.auditEvents.reduce((max, event) => Math.max(max, event.seq), 0) + 1;
  deal.auditEvents.push({
    seq,
    dealId: deal.id,
    type,
    actorRole,
    summary,
    at: nowIso(),
    metadata,
  });
  deal.updatedAt = nowIso();
}

function validateTerms(terms: StructuredTermsDTO): void {
  if (!terms.title.trim()) fail("A deal title is required.");
  if (!terms.currency.trim()) fail("A currency is required.");
  if (!Number.isInteger(terms.priceMinorUnits) || terms.priceMinorUnits <= 0) {
    fail("Price must be a positive integer amount in minor units.");
  }
  if (!Number.isInteger(terms.deliveryDays) || terms.deliveryDays < 1) {
    fail("Delivery days must be at least 1.");
  }
  if (!Number.isInteger(terms.includedRevisions) || terms.includedRevisions < 0) {
    fail("Included revisions cannot be negative.");
  }
  if (terms.deliverables.filter((item) => item.trim()).length === 0) {
    fail("At least one deliverable is required.");
  }
  if (!terms.acceptanceCriteriaText.trim()) {
    fail("Acceptance criteria are required.");
  }
}

function validateMandateInput(input: MandateInput, role: PartyRole): void {
  if (!input.currency.trim()) fail("A mandate currency is required.");
  if (!Number.isInteger(input.limitPriceMinorUnits) || input.limitPriceMinorUnits <= 0) {
    fail("Limit price must be a positive integer in minor units.");
  }
  if (!Number.isInteger(input.targetPriceMinorUnits) || input.targetPriceMinorUnits <= 0) {
    fail("Target price must be a positive integer in minor units.");
  }
  if (role === "BUYER" && input.targetPriceMinorUnits > input.limitPriceMinorUnits) {
    fail("For a buyer, the target price cannot exceed the limit.");
  }
  if (role === "SELLER" && input.targetPriceMinorUnits < input.limitPriceMinorUnits) {
    fail("For a seller, the target price cannot be below the limit.");
  }
  if (input.limitDeliveryDays < 1 || input.targetDeliveryDays < 1) {
    fail("Delivery limits must be at least 1 day.");
  }
  if (input.limitRevisions < 0 || input.targetRevisions < 0) {
    fail("Revision limits cannot be negative.");
  }
  if (input.maxRounds < 1) fail("Maximum rounds must be at least 1.");
  if (Number.isNaN(new Date(input.expiresAt).getTime())) {
    fail("A valid mandate expiry is required.");
  }
}

function emptySettlement(dealId: string, amountMinorUnits: number, currency: string): SettlementStateDTO {
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
    amountMinorUnits,
    currency,
    lastReconciledAt: null,
  };
}

export function buildWorkspace(
  db: MockDatabase,
  dealId: string,
  userId: string,
): DealWorkspaceDTO | null {
  const deal = db.deals[dealId];
  if (!deal) return null;
  const role = roleFor(deal, userId);
  if (!role) return null;

  return {
    dealId: deal.id,
    status: deal.status,
    stopReason: deal.stopReason,
    buyerEmail: demoUserById(deal.buyerUserId)?.email ?? "buyer@example.test",
    sellerEmail: deal.sellerUserId ? demoUserById(deal.sellerUserId)?.email ?? null : null,
    myRole: role,
    myMandate: role === "BUYER" ? deal.buyerMandate : deal.sellerMandate,
    offers: deal.offers.map((item) => ({
      ...item,
      terms: { ...item.terms, deliverables: [...item.terms.deliverables] },
    })),
    currentAgreement: deal.currentAgreement
      ? {
          ...deal.currentAgreement,
          canonicalTerms: {
            ...deal.currentAgreement.canonicalTerms,
            deliverables: [...deal.currentAgreement.canonicalTerms.deliverables],
          },
        }
      : null,
    settlement: { ...deal.settlement },
    evidenceList: deal.evidenceList.map((item) => ({ ...item })),
    disputeStatus: deal.disputeStatus,
    disputeReason: deal.disputeReason,
  };
}

export function getDealSummaries(db: MockDatabase, userId: string): MockDealSummary[] {
  return Object.values(db.deals)
    .flatMap((deal) => {
      const role = roleFor(deal, userId);
      if (!role) return [];
      const counterpartyId = role === "BUYER" ? deal.sellerUserId : deal.buyerUserId;
      return [
        {
          dealId: deal.id,
          title: deal.currentAgreement?.canonicalTerms.title ?? deal.terms.title,
          status: deal.status,
          stopReason: deal.stopReason,
          myRole: role,
          counterpartyEmail: counterpartyId ? demoUserById(counterpartyId)?.email ?? null : null,
          offerCount: deal.offers.length,
          settlement: { ...deal.settlement },
          disputeStatus: deal.disputeStatus,
          updatedAt: deal.updatedAt,
        },
      ];
    })
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

export function createIntake(db: MockDatabase, userId: string, input: IntakeInput): IntakeResult {
  validateTerms(input.terms);
  const dealId = newId("deal-demo");
  const inviteToken = newId("invite-demo");
  const createdAt = nowIso();
  const deal: MockDeal = {
    id: dealId,
    buyerUserId: userId,
    sellerUserId: null,
    status: "ACTIVE_NEGOTIATION",
    stopReason: "NONE",
    terms: { ...input.terms, deliverables: [...input.terms.deliverables] },
    buyerMandate: null,
    sellerMandate: null,
    offers: [],
    currentAgreement: null,
    settlement: emptySettlement(dealId, input.terms.priceMinorUnits, input.terms.currency),
    evidenceList: [],
    disputeStatus: "NONE",
    disputeReason: null,
    inviteToken,
    createdAt,
    updatedAt: createdAt,
    auditEvents: [
      {
        seq: 1,
        dealId,
        type: "DEAL_CREATED",
        actorRole: "BUYER",
        summary: "Deal created from structured intake in demo mode.",
        at: createdAt,
        metadata: { currency: input.terms.currency },
      },
    ],
  };
  db.deals[dealId] = deal;
  return {
    dealId,
    terms: { ...input.terms, deliverables: [...input.terms.deliverables] },
    inviteToken,
  };
}

export function joinDeal(db: MockDatabase, userId: string, dealId: string, inviteToken: string): void {
  const deal = requireDeal(db, dealId);
  if (deal.buyerUserId === userId) fail("The buyer cannot join their own deal as the seller.");
  if (deal.sellerUserId === userId) fail("You already joined this deal as the seller.");
  if (deal.sellerUserId) fail("This deal already has a seller.");
  if (!inviteToken.trim() || inviteToken.trim() !== deal.inviteToken) {
    fail("That invite token does not match this deal.");
  }
  deal.sellerUserId = userId;
  pushAudit(deal, "SELLER_JOINED", "SELLER", "Seller joined the deal with a valid invite token.");
}

export function saveMandate(
  db: MockDatabase,
  userId: string,
  dealId: string,
  input: MandateInput,
): MandateDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  assertMutableNegotiation(deal);
  validateMandateInput(input, role);

  const current = role === "BUYER" ? deal.buyerMandate : deal.sellerMandate;
  const mandate: MandateDTO = {
    mandateId: newId("mandate"),
    dealId,
    partyRole: role,
    version: (current?.version ?? 0) + 1,
    ...input,
  };

  if (role === "BUYER") deal.buyerMandate = mandate;
  else deal.sellerMandate = mandate;

  pushAudit(
    deal,
    "MANDATE_LOCKED",
    role,
    `${role === "BUYER" ? "Buyer" : "Seller"} locked mandate v${mandate.version}.`,
    { version: mandate.version },
  );
  return mandate;
}

function offerAgainstMandate(
  deal: MockDeal,
  userId: string,
  terms: StructuredTermsDTO,
  message: string,
  generatedByAgent: boolean,
): OfferDTO {
  const role = requireRole(deal, userId);
  assertMutableNegotiation(deal);
  if (deal.status !== "ACTIVE_NEGOTIATION") {
    fail("Offers can only be submitted during active negotiation.");
  }
  validateTerms(terms);

  const mandate = role === "BUYER" ? deal.buyerMandate : deal.sellerMandate;
  if (!mandate) fail("Lock your mandate before submitting an offer.");

  if (terms.priceMinorUnits > mandate.limitPriceMinorUnits) {
    fail("Offer price exceeds your mandate limit. Demo validation mirrors the API boundary.");
  }
  if (terms.deliveryDays > mandate.limitDeliveryDays) {
    fail("Offer delivery exceeds your mandate delivery limit.");
  }
  if (terms.includedRevisions < mandate.limitRevisions) {
    fail("Offer revisions are below your mandate revision limit.");
  }

  const roundNumber = deal.offers.reduce((max, item) => Math.max(max, item.roundNumber), 0) + 1;
  const offer: OfferDTO = {
    offerId: newId("offer-demo"),
    dealId: deal.id,
    roundNumber,
    proposerRole: role,
    generatedByAgent,
    terms: { ...terms, deliverables: [...terms.deliverables] },
    rationaleSummary: message.trim() || "No rationale provided.",
    callerUtilityScore: "DEMO_UNSCORED",
    mandateCompliant: true,
    createdAt: nowIso(),
  };
  deal.offers.push(offer);
  pushAudit(
    deal,
    "OFFER_SUBMITTED",
    role,
    `${role === "BUYER" ? "Buyer" : "Seller"} submitted round ${roundNumber} terms${generatedByAgent ? " from the demo agent" : ""}.`,
    { round: roundNumber, priceMinorUnits: terms.priceMinorUnits, currency: terms.currency, generatedByAgent },
  );
  return offer;
}

export function submitOffer(
  db: MockDatabase,
  userId: string,
  dealId: string,
  terms: StructuredTermsDTO,
  message: string,
): OfferDTO {
  const deal = requireDeal(db, dealId);
  return offerAgainstMandate(deal, userId, terms, message, false);
}

export function generateAgentOffer(db: MockDatabase, userId: string, dealId: string): OfferDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  const mandate = role === "BUYER" ? deal.buyerMandate : deal.sellerMandate;
  if (!mandate) fail("Lock your mandate before requesting a bounded agent move.");
  const latest = deal.offers[deal.offers.length - 1];
  if (!latest) fail("Submit a first offer before requesting an agent counteroffer.");

  const midpoint = (from: number, to: number) => Math.round((from + to) / 2);
  const terms: StructuredTermsDTO = {
    ...latest.terms,
    priceMinorUnits: midpoint(latest.terms.priceMinorUnits, mandate.targetPriceMinorUnits),
    deliveryDays: midpoint(latest.terms.deliveryDays, mandate.targetDeliveryDays),
    includedRevisions: mandate.targetRevisions,
  };
  return offerAgainstMandate(
    deal,
    userId,
    terms,
    "Demo bounded agent move: midpoint between the latest terms and your mandate target. Live mode requests this from the server negotiation engine.",
    true,
  );
}

export function proposeAgreement(
  db: MockDatabase,
  userId: string,
  dealId: string,
  terms: StructuredTermsDTO,
  termsHashSha256: string,
): AgreementSnapshotDTO {
  const deal = requireDeal(db, dealId);
  requireRole(deal, userId);
  assertMutableNegotiation(deal);
  validateTerms(terms);
  if (!termsHashSha256 || termsHashSha256.length < 32) fail("A demo terms hash could not be created.");

  if (deal.currentAgreement) {
    deal.currentAgreement.buyerAcceptedAt = null;
    deal.currentAgreement.sellerAcceptedAt = null;
  }

  const version = (deal.currentAgreement?.version ?? 0) + 1;
  const agreement: AgreementSnapshotDTO = {
    agreementId: newId("agreement-demo"),
    dealId,
    version,
    canonicalTerms: { ...terms, deliverables: [...terms.deliverables] },
    termsHashSha256,
    buyerAcceptedAt: null,
    sellerAcceptedAt: null,
  };
  deal.currentAgreement = agreement;
  deal.status = "TERMS_PENDING_ACCEPTANCE";
  deal.stopReason = "NONE";

  pushAudit(deal, "AGREEMENT_PROPOSED", null, `Agreement v${version} proposed for dual consent.`, {
    version,
    termsHashSha256,
  });
  return agreement;
}

export function acceptAgreement(
  db: MockDatabase,
  userId: string,
  dealId: string,
  version: number,
  termsHashSha256: string,
): AgreementSnapshotDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  if (deal.status === "DISPUTED" || deal.status === "CLOSED" || deal.status === "CANCELLED") {
    fail(`Agreement acceptance is not available while the deal is ${deal.status}.`);
  }
  const agreement = deal.currentAgreement;
  if (!agreement) fail("No agreement has been proposed for this deal.");
  if (agreement.version !== version) fail("This agreement version is no longer current.");
  if (agreement.termsHashSha256 !== termsHashSha256) {
    fail("The agreement hash does not match. Refresh the agreement before accepting.");
  }

  const acceptedAt = nowIso();
  if (role === "BUYER") {
    if (agreement.buyerAcceptedAt) fail("You already accepted this agreement version.");
    agreement.buyerAcceptedAt = acceptedAt;
  } else {
    if (agreement.sellerAcceptedAt) fail("You already accepted this agreement version.");
    agreement.sellerAcceptedAt = acceptedAt;
  }

  if (agreement.buyerAcceptedAt && agreement.sellerAcceptedAt) {
    deal.status = "AGREED";
    deal.stopReason = "CONVERGED";
  }

  pushAudit(
    deal,
    "AGREEMENT_ACCEPTED",
    role,
    `${role === "BUYER" ? "Buyer" : "Seller"} accepted agreement v${version}.`,
    { version },
  );
  return agreement;
}

export function initiateSettlement(
  db: MockDatabase,
  userId: string,
  dealId: string,
): SettlementStateDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  if (role !== "BUYER") fail("Only the buyer can initiate the sandbox settlement.");
  assertNotFrozen(deal);
  if (deal.status !== "AGREED" && deal.status !== "SETTLEMENT_PENDING") {
    fail("The deal must be agreed before settlement can be initiated.");
  }
  if (!deal.currentAgreement) fail("No agreed terms are available.");
  if (deal.settlement.invoiceStatus !== "NOT_CREATED") {
    fail("Settlement has already been initiated for this deal.");
  }

  const reference = newId("DEMO").replace("DEMO-", "");
  deal.settlement = {
    ...deal.settlement,
    agreementVersion: deal.currentAgreement.version,
    invoiceStatus: "SENT",
    paypalInvoiceId: `DEMO-INV-${reference}`,
    paypalInvoiceNumber: `DEMO-${reference.slice(0, 8).toUpperCase()}`,
    paypalInvoiceViewUrl: null,
    paymentStatus: "ORDER_CREATED",
    paypalOrderId: `DEMO-ORD-${reference}`,
    amountMinorUnits: deal.currentAgreement.canonicalTerms.priceMinorUnits,
    currency: deal.currentAgreement.canonicalTerms.currency,
    lastReconciledAt: nowIso(),
  };
  deal.status = "SETTLEMENT_PENDING";

  pushAudit(deal, "INVOICE_SENT", "BUYER", "Demo sandbox invoice created and an order was prepared.", {
    amountMinorUnits: deal.settlement.amountMinorUnits,
    currency: deal.settlement.currency,
  });
  return { ...deal.settlement };
}

export function authorizeSettlement(
  db: MockDatabase,
  userId: string,
  dealId: string,
): SettlementStateDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  if (role !== "BUYER") fail("Only the buyer can authorize the hold.");
  assertNotFrozen(deal);
  if (deal.settlement.paymentStatus !== "ORDER_CREATED") {
    fail("There is no order waiting for authorization.");
  }

  const expires = new Date();
  expires.setDate(expires.getDate() + 29);
  deal.settlement = {
    ...deal.settlement,
    paymentStatus: "AUTHORIZED",
    paypalAuthorizationId: `DEMO-AUTH-${newId("ID").slice(-12).toUpperCase()}`,
    authorizationExpiresAt: expires.toISOString(),
    lastReconciledAt: nowIso(),
  };
  deal.status = "IN_PROGRESS";

  pushAudit(deal, "PAYMENT_AUTHORIZED", "BUYER", "Demo authorization hold created.", {
    amountMinorUnits: deal.settlement.amountMinorUnits,
    currency: deal.settlement.currency,
  });
  return { ...deal.settlement };
}

export function voidSettlement(
  db: MockDatabase,
  userId: string,
  dealId: string,
  reason: string,
): SettlementStateDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  if (role !== "BUYER") fail("Only the buyer can void the authorization.");
  const voidable =
    deal.settlement.paymentStatus === "ORDER_CREATED" ||
    deal.settlement.paymentStatus === "AUTHORIZED";
  if (!voidable) fail("There is no open authorization to void.");

  deal.settlement = {
    ...deal.settlement,
    invoiceStatus: "CANCELLED",
    paymentStatus: "VOIDED",
    lastReconciledAt: nowIso(),
  };
  deal.status = "CANCELLED";
  deal.stopReason = "USER_PAUSED";

  pushAudit(deal, "SETTLEMENT_VOIDED", "BUYER", reason.trim() || "Buyer voided the demo authorization.");
  return { ...deal.settlement };
}

export function uploadEvidence(
  db: MockDatabase,
  userId: string,
  dealId: string,
  input: {
    filename: string;
    mimeType: string;
    sizeBytes: number;
    sha256Hash: string;
  },
): DeliverableEvidenceDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  if (role !== "SELLER") fail("Only the seller can upload deliverable evidence.");
  assertNotFrozen(deal);
  if (deal.status !== "IN_PROGRESS" && deal.status !== "DELIVERABLES_SUBMITTED") {
    fail("Deliverables can be uploaded while the deal is in progress.");
  }
  if (input.sizeBytes <= 0 || input.sizeBytes > MAX_EVIDENCE_BYTES) {
    fail("Evidence must be between 1 byte and 10 MB.");
  }
  if (!ALLOWED_EVIDENCE_TYPES.has(input.mimeType)) {
    fail("That file type is not in the demo allowlist (PDF, ZIP, PNG, JPEG, plain text).");
  }
  if (!/^[a-f0-9]{64}$/i.test(input.sha256Hash)) {
    fail("A SHA-256 hash could not be computed for this file.");
  }

  const version = deal.evidenceList.reduce((max, item) => Math.max(max, item.version), 0) + 1;
  const evidence: DeliverableEvidenceDTO = {
    evidenceId: newId("evidence-demo"),
    dealId,
    uploaderRole: "SELLER",
    filename: input.filename,
    mimeType: input.mimeType,
    sizeBytes: input.sizeBytes,
    sha256Hash: input.sha256Hash.toLowerCase(),
    version,
    downloadSignedUrl: "",
    uploadedAt: nowIso(),
  };
  deal.evidenceList.push(evidence);
  deal.status = "DELIVERABLES_SUBMITTED";

  pushAudit(deal, "EVIDENCE_UPLOADED", "SELLER", `Seller uploaded deliverable evidence v${version}.`, {
    version,
    sizeBytes: input.sizeBytes,
    mimeType: input.mimeType,
    sha256Hash: evidence.sha256Hash,
  });
  return { ...evidence };
}

export function acceptDeliverables(
  db: MockDatabase,
  userId: string,
  dealId: string,
  evidenceVersion: number,
): SettlementStateDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  if (role !== "BUYER") fail("Only the buyer can accept deliverables.");
  assertNotFrozen(deal);
  if (deal.status !== "DELIVERABLES_SUBMITTED") {
    fail("Deliverables are not awaiting acceptance.");
  }
  if (!deal.evidenceList.some((item) => item.version === evidenceVersion)) {
    fail("That evidence version does not exist.");
  }

  deal.settlement = {
    ...deal.settlement,
    paymentStatus: "COMPLETED",
    invoiceStatus: "PAID",
    paypalCaptureId: `DEMO-CAP-${newId("ID").slice(-12).toUpperCase()}`,
    lastReconciledAt: nowIso(),
  };
  deal.status = "CLOSED";
  deal.stopReason = "CONVERGED";

  pushAudit(deal, "DELIVERABLES_ACCEPTED", "BUYER", "Buyer accepted deliverables and released the demo capture.", {
    evidenceVersion,
    amountMinorUnits: deal.settlement.amountMinorUnits,
    currency: deal.settlement.currency,
  });
  return { ...deal.settlement };
}

export function openDispute(
  db: MockDatabase,
  userId: string,
  dealId: string,
  reason: string,
  resolutionRequest: string,
): DealWorkspaceDTO {
  const deal = requireDeal(db, dealId);
  const role = requireRole(deal, userId);
  if (role !== "BUYER") fail("Only the buyer can open a dispute in this demo.");
  if (deal.disputeStatus !== "NONE") fail("A dispute is already open for this deal.");
  if (deal.status !== "IN_PROGRESS" && deal.status !== "DELIVERABLES_SUBMITTED") {
    fail("A dispute can be opened after the hold is authorized.");
  }
  if (!reason.trim()) fail("A dispute reason is required.");

  deal.disputeStatus = "OPEN";
  deal.disputeReason = reason.trim();
  deal.status = "DISPUTED";
  deal.stopReason = "USER_PAUSED";

  pushAudit(
    deal,
    "DISPUTE_OPENED",
    "BUYER",
    "Buyer opened a dispute and capture was frozen.",
    resolutionRequest.trim() ? { resolutionRequest: resolutionRequest.trim() } : undefined,
  );
  return buildWorkspace(db, dealId, userId) as DealWorkspaceDTO;
}
