// Canonical shared contracts — mirror com.payagent.common.contracts (Member A / Member D).

export type PartyRole = "BUYER" | "SELLER";
export type NegotiationMode = "ASSISTED" | "AUTONOMOUS_BOUNDED";

export type DealStatus =
  | "DRAFT"
  | "INVITED"
  | "ACTIVE_NEGOTIATION"
  | "TERMS_PENDING_ACCEPTANCE"
  | "AGREED"
  | "SETTLEMENT_PENDING"
  | "IN_PROGRESS"
  | "DELIVERABLES_SUBMITTED"
  | "ACCEPTED"
  | "DISPUTED"
  | "CLOSED"
  | "CANCELLED";

export type NegotiationStopReason =
  | "NONE"
  | "CONVERGED"
  | "NO_ZOPA_DETECTED"
  | "MAX_ROUNDS_REACHED"
  | "MANDATE_EXPIRED"
  | "USER_PAUSED"
  | "POLICY_VIOLATION_BLOCKED";

export type InvoiceLifecycleStatus =
  | "NOT_CREATED"
  | "DRAFT"
  | "SENT"
  | "PAID"
  | "CANCELLED"
  | "UNKNOWN";

export type PaymentLifecycleStatus =
  | "NOT_STARTED"
  | "ORDER_CREATED"
  | "AUTHORIZED"
  | "CAPTURE_PENDING"
  | "COMPLETED"
  | "VOIDED"
  | "DECLINED"
  | "UNKNOWN";

export type DisputeStatus =
  | "NONE"
  | "OPEN"
  | "UNDER_PARTIES_REVIEW"
  | "RESOLVED_RELEASE_CAPTURE"
  | "RESOLVED_VOID_AUTHORIZATION";

export interface StructuredTermsDTO {
  title: string;
  deliverables: string[];
  priceMinorUnits: number;
  currency: string;
  deliveryDays: number;
  includedRevisions: number;
  acceptanceCriteriaText: string;
}

export interface MandateDTO {
  mandateId: string;
  dealId: string;
  partyRole: PartyRole;
  version: number;
  currency: string;
  limitPriceMinorUnits: number;
  targetPriceMinorUnits: number;
  limitDeliveryDays: number;
  targetDeliveryDays: number;
  limitRevisions: number;
  targetRevisions: number;
  mustHaveDeliverables: string[];
  forbiddenTerms: string[];
  mode: NegotiationMode;
  maxRounds: number;
  expiresAt: string;
}

export interface OfferDTO {
  offerId: string;
  dealId: string;
  roundNumber: number;
  proposerRole: PartyRole;
  generatedByAgent: boolean;
  terms: StructuredTermsDTO;
  rationaleSummary: string;
  callerUtilityScore: string;
  mandateCompliant: boolean;
  createdAt: string;
}

export interface AgreementSnapshotDTO {
  agreementId: string;
  dealId: string;
  version: number;
  canonicalTerms: StructuredTermsDTO;
  termsHashSha256: string;
  buyerAcceptedAt: string | null;
  sellerAcceptedAt: string | null;
}

export interface SettlementStateDTO {
  dealId: string;
  agreementVersion: number;
  invoiceStatus: InvoiceLifecycleStatus;
  paypalInvoiceId: string | null;
  paypalInvoiceNumber: string | null;
  paypalInvoiceViewUrl: string | null;
  paymentStatus: PaymentLifecycleStatus;
  paypalOrderId: string | null;
  paypalAuthorizationId: string | null;
  paypalCaptureId: string | null;
  authorizationExpiresAt: string | null;
  amountMinorUnits: number;
  currency: string;
  lastReconciledAt: string | null;
}

export interface DeliverableEvidenceDTO {
  evidenceId: string;
  dealId: string;
  uploaderRole: PartyRole;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  sha256Hash: string;
  version: number;
  downloadSignedUrl: string;
  uploadedAt: string;
}

export interface DealWorkspaceDTO {
  dealId: string;
  status: DealStatus;
  stopReason: NegotiationStopReason;
  buyerEmail: string;
  sellerEmail: string | null;
  myRole: PartyRole;
  myMandate: MandateDTO | null;
  offers: OfferDTO[];
  currentAgreement: AgreementSnapshotDTO | null;
  settlement: SettlementStateDTO;
  evidenceList: DeliverableEvidenceDTO[];
  disputeStatus: DisputeStatus;
  disputeReason: string | null;
}
