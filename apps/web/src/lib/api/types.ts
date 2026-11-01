import type { StructuredTermsDTO } from "@/types/contracts";

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
}

export interface IntakeResponse {
  dealId: string;
  terms: StructuredTermsDTO;
  inviteToken?: string;
}

export interface CreateDealResponse {
  dealId: string;
}
