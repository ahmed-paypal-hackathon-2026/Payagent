import type {
  AgreementSnapshotDTO,
  DealWorkspaceDTO,
  DeliverableEvidenceDTO,
  MandateDTO,
  OfferDTO,
  SettlementStateDTO,
  StructuredTermsDTO,
} from "@/types/contracts";
import type { AuthResponse, CreateDealResponse, IntakeResponse } from "./types";

export const USE_MOCKS = process.env.NEXT_PUBLIC_USE_MOCKS !== "false";
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080").replace(
  /\/+$/,
  "",
);

export class ApiError extends Error {
  readonly status: number;
  readonly endpoint: string;
  readonly detail: string | null;

  constructor(endpoint: string, status: number, message: string, detail: string | null = null) {
    super(message);
    this.name = "ApiError";
    this.endpoint = endpoint;
    this.status = status;
    this.detail = detail;
  }
}

export class ApiNetworkError extends Error {
  readonly endpoint: string;

  constructor(endpoint: string) {
    super(
      `Could not reach the Payagent API at ${API_BASE_URL}. Check that the backend is running and that its CORS policy allows this origin.`,
    );
    this.name = "ApiNetworkError";
    this.endpoint = endpoint;
  }
}

interface RequestOptions {
  method?: "GET" | "POST";
  token?: string | null;
  body?: unknown;
  form?: FormData;
  idempotencyKey?: string;
}

export interface DisputeMutationResult {
  id: string;
  dealId: string;
  status: string;
  reason: string;
  resolutionRequest: string | null;
  openedAt: string;
  resolvedAt: string | null;
}

export interface AuditEventApiDTO {
  id: number | null;
  dealId: string;
  seq: number;
  eventType: string;
  payload: unknown;
  createdAt: string;
}

function idempotencyKey(): string {
  return globalThis.crypto?.randomUUID?.() ?? `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function readError(response: Response): Promise<{ message: string; detail: string | null }> {
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    // Non-JSON error bodies fall back to the status text.
  }

  if (body && typeof body === "object") {
    const record = body as Record<string, unknown>;
    const detail = typeof record.detail === "string" ? record.detail : null;
    const message = typeof record.message === "string" ? record.message : null;
    const error = typeof record.error === "string" ? record.error : null;
    if (message) return { message, detail };
    if (detail) return { message: detail, detail };
    if (error) return { message: error, detail };
  }

  if (response.status === 401) {
    return { message: "Your session is missing or expired. Sign in again.", detail: null };
  }
  if (response.status === 403) {
    return { message: "You do not have access to this deal action.", detail: null };
  }
  if (response.status === 404) {
    return { message: "The requested resource was not found.", detail: null };
  }
  if (response.status === 409) {
    return { message: "The request conflicts with the current server state.", detail: null };
  }
  return { message: `The API returned ${response.status} ${response.statusText}.`, detail: null };
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({ Accept: "application/json" });
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (options.token) headers.set("Authorization", `Bearer ${options.token}`);
  if (options.idempotencyKey) headers.set("Idempotency-Key", options.idempotencyKey);

  const payload = options.form ?? (options.body === undefined ? undefined : JSON.stringify(options.body));

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? (options.form ? "POST" : "GET"),
      headers,
      body: payload,
      cache: "no-store",
    });
  } catch {
    throw new ApiNetworkError(path);
  }

  if (!response.ok) {
    const parsed = await readError(response);
    throw new ApiError(path, response.status, parsed.message, parsed.detail);
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError(path, response.status, "The API returned an unreadable response.", null);
  }
}

function dealPath(dealId: string, suffix = ""): string {
  return `/api/v1/deals/${encodeURIComponent(dealId)}${suffix}`;
}

export const api = {
  health(): Promise<{ status: string; service?: string }> {
    return request("/api/v1/health");
  },

  login(email: string, password: string): Promise<AuthResponse> {
    return request("/api/v1/auth/login", { method: "POST", body: { email, password } });
  },

  register(email: string, password: string): Promise<AuthResponse> {
    return request("/api/v1/auth/register", { method: "POST", body: { email, password } });
  },

  intake(rawPrompt: string, currency: string, token: string): Promise<IntakeResponse> {
    return request("/api/v1/deals/intake", {
      method: "POST",
      token,
      body: { rawPrompt, currency },
    });
  },

  createDeal(token: string): Promise<CreateDealResponse> {
    return request("/api/v1/deals", {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
    });
  },

  joinDeal(dealId: string, inviteToken: string, token: string): Promise<void> {
    return request(dealPath(dealId, "/join"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: { inviteToken },
    });
  },

  getMandate(dealId: string, token: string): Promise<MandateDTO> {
    return request(dealPath(dealId, "/mandates"), { token });
  },

  createMandate(
    dealId: string,
    token: string,
    input: Omit<MandateDTO, "mandateId" | "dealId" | "partyRole" | "version">,
  ): Promise<MandateDTO> {
    return request(dealPath(dealId, "/mandates"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: input,
    });
  },

  createOffer(
    dealId: string,
    token: string,
    terms: StructuredTermsDTO,
    message: string,
  ): Promise<OfferDTO> {
    return request(dealPath(dealId, "/offers"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: { terms, message },
    });
  },

  proposeAgreement(dealId: string, token: string, terms: StructuredTermsDTO): Promise<AgreementSnapshotDTO> {
    return request(dealPath(dealId, "/agreements/propose"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: terms,
    });
  },

  acceptAgreement(
    dealId: string,
    version: number,
    termsHashSha256: string,
    token: string,
  ): Promise<AgreementSnapshotDTO> {
    return request(dealPath(dealId, `/agreements/${version}/accept`), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: { termsHashSha256 },
    });
  },

  getSettlement(dealId: string, token: string): Promise<SettlementStateDTO> {
    return request(dealPath(dealId, "/settlement"), { token });
  },

  getDealWorkspace(dealId: string, token: string): Promise<DealWorkspaceDTO> {
    return request(dealPath(dealId, "/workspace"), { token });
  },

  getAuditEvents(dealId: string, token: string, afterSeq = 0): Promise<AuditEventApiDTO[]> {
    return request(dealPath(dealId, `/events?afterSeq=${Number.isInteger(afterSeq) && afterSeq > 0 ? afterSeq : 0}`), {
      token,
    });
  },

  uploadEvidence(dealId: string, token: string, file: File): Promise<DeliverableEvidenceDTO> {
    const form = new FormData();
    form.append("file", file, file.name);
    return request(dealPath(dealId, "/evidence"), {
      method: "POST",
      token,
      form,
      idempotencyKey: idempotencyKey(),
    });
  },

  openDispute(
    dealId: string,
    token: string,
    reason: string,
    resolutionRequest: string | null,
  ): Promise<DisputeMutationResult> {
    return request(dealPath(dealId, "/disputes"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: { reason, resolutionRequest: resolutionRequest ?? null },
    });
  },

  initiateSettlement(dealId: string, token: string): Promise<SettlementStateDTO> {
    return request(dealPath(dealId, "/settlement/initiate"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
    });
  },

  authorizeSettlement(dealId: string, paypalOrderId: string, token: string): Promise<SettlementStateDTO> {
    return request(dealPath(dealId, "/settlement/authorize"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: { paypalOrderId },
    });
  },

  voidSettlement(dealId: string, reason: string, token: string): Promise<SettlementStateDTO> {
    return request(dealPath(dealId, "/settlement/void"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: { reason },
    });
  },

  acceptDeliverables(
    dealId: string,
    evidenceVersion: number,
    token: string,
  ): Promise<SettlementStateDTO> {
    return request(dealPath(dealId, "/accept-deliverables"), {
      method: "POST",
      token,
      idempotencyKey: idempotencyKey(),
      body: { evidenceVersion },
    });
  },
};

export type { DealWorkspaceDTO };
