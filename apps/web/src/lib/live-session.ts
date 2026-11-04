import type {
  AgreementSnapshotDTO,
  DealStatus,
  OfferDTO,
  PartyRole,
  StructuredTermsDTO,
} from "@/types/contracts";

export interface LiveDealSession {
  dealId: string;
  roleHint?: PartyRole;
  terms?: StructuredTermsDTO;
  inviteToken?: string;
  statusHint?: DealStatus;
  currentAgreement?: AgreementSnapshotDTO | null;
  offers?: OfferDTO[];
  updatedAt?: string;
}

const DEAL_PREFIX = "payagent.live.deal.";

function keyFor(dealId: string): string {
  return `${DEAL_PREFIX}${dealId}`;
}

export function readLiveDealSession(dealId: string): LiveDealSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(keyFor(dealId));
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return null;
    const session = parsed as LiveDealSession;
    return session.dealId === dealId ? session : null;
  } catch {
    return null;
  }
}

export function writeLiveDealSession(
  dealId: string,
  patch: Partial<LiveDealSession>,
): LiveDealSession {
  const current = readLiveDealSession(dealId) ?? { dealId };
  const next: LiveDealSession = {
    ...current,
    ...patch,
    dealId,
    updatedAt: new Date().toISOString(),
  };
  if (typeof window !== "undefined") {
    try {
      window.sessionStorage.setItem(keyFor(dealId), JSON.stringify(next));
    } catch {
      // Session persistence is a convenience; the live action result still renders.
    }
  }
  return next;
}

export function clearLiveDealSession(dealId: string): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(keyFor(dealId));
  } catch {
    // Ignore storage failures.
  }
}

/** Removes per-deal hints when the authenticated user changes. */
export function clearAllLiveDealSessions(): void {
  if (typeof window === "undefined") return;
  try {
    const keys: string[] = [];
    for (let index = 0; index < window.sessionStorage.length; index += 1) {
      const key = window.sessionStorage.key(index);
      if (key?.startsWith(DEAL_PREFIX)) keys.push(key);
    }
    for (const key of keys) window.sessionStorage.removeItem(key);
  } catch {
    // Ignore storage failures.
  }
}
