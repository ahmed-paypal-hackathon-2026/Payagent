import type {
  AgreementSnapshotDTO,
  DealStatus,
  DeliverableEvidenceDTO,
  DisputeStatus,
  MandateDTO,
  NegotiationStopReason,
  OfferDTO,
  PartyRole,
  SettlementStateDTO,
  StructuredTermsDTO,
} from "@/types/contracts";

export interface DemoUser {
  id: string;
  email: string;
  displayName: string;
  roleLabel: string;
}

export interface AuditEvent {
  seq: number;
  dealId: string;
  type: string;
  actorRole: PartyRole | null;
  summary: string;
  at: string;
  metadata?: Record<string, string | number | boolean | null>;
}

export interface MockDeal {
  id: string;
  buyerUserId: string;
  sellerUserId: string | null;
  status: DealStatus;
  stopReason: NegotiationStopReason;
  terms: StructuredTermsDTO;
  buyerMandate: MandateDTO | null;
  sellerMandate: MandateDTO | null;
  offers: OfferDTO[];
  currentAgreement: AgreementSnapshotDTO | null;
  settlement: SettlementStateDTO;
  evidenceList: DeliverableEvidenceDTO[];
  disputeStatus: DisputeStatus;
  disputeReason: string | null;
  inviteToken: string;
  createdAt: string;
  updatedAt: string;
  auditEvents: AuditEvent[];
}

export interface MockDatabase {
  schemaVersion: 1;
  deals: Record<string, MockDeal>;
}

export interface MockDealSummary {
  dealId: string;
  title: string;
  status: DealStatus;
  stopReason: NegotiationStopReason;
  myRole: PartyRole;
  counterpartyEmail: string | null;
  offerCount: number;
  settlement: SettlementStateDTO;
  disputeStatus: DisputeStatus;
  updatedAt: string;
}

export type MandateInput = Omit<MandateDTO, "mandateId" | "dealId" | "partyRole" | "version">;

export interface IntakeInput {
  rawPrompt: string;
  currency: string;
  terms: StructuredTermsDTO;
}

export interface IntakeResult {
  dealId: string;
  terms: StructuredTermsDTO;
  inviteToken: string;
}
