export type CapabilityState = "available" | "pending";

export interface LiveCapability {
  id: string;
  label: string;
  state: CapabilityState;
  owner: string;
  endpoint: string;
  note: string;
}

export const LIVE_CAPABILITIES: LiveCapability[] = [
  {
    id: "auth",
    label: "Sign in and register",
    state: "available",
    owner: "Member A",
    endpoint: "POST /api/v1/auth/login, /register",
    note: "JWT is kept in session storage for this tab only.",
  },
  {
    id: "intake",
    label: "Intake extraction",
    state: "available",
    owner: "Member A / C",
    endpoint: "POST /api/v1/deals/intake",
    note: "The server returns the extracted structured terms.",
  },
  {
    id: "mandates",
    label: "Own mandate read and versioned write",
    state: "available",
    owner: "Member A",
    endpoint: "GET/POST /api/v1/deals/{id}/mandates",
    note: "The UI only requests the authenticated user's mandate.",
  },
  {
    id: "offers-create",
    label: "Submit an offer",
    state: "available",
    owner: "Member A",
    endpoint: "POST /api/v1/deals/{id}/offers",
    note: "The server validates the caller's mandate and returns the accepted offer.",
  },
  {
    id: "agreements",
    label: "Propose and accept an agreement",
    state: "available",
    owner: "Member A",
    endpoint: "POST /api/v1/deals/{id}/agreements/...",
    note: "Acceptance uses the server-issued version and hash.",
  },
  {
    id: "settlement",
    label: "Settlement status and actions",
    state: "available",
    owner: "Member B",
    endpoint: "GET/POST /api/v1/deals/{id}/settlement/...",
    note: "Invoice and authorization identifiers are rendered only when the API returns them.",
  },
  {
    id: "workspace",
    label: "Deal workspace snapshot",
    state: "available",
    owner: "Member A",
    endpoint: "GET /api/v1/deals/{id}/workspace",
    note: "One verified response with status, own mandate, offers, agreement, settlement, evidence, and dispute state.",
  },
  {
    id: "offers-list",
    label: "Offer history",
    state: "available",
    owner: "Member A",
    endpoint: "GET /api/v1/deals/{id}/workspace",
    note: "Served inside the workspace snapshot; there is no standalone offer-list endpoint.",
  },
  {
    id: "agreement-read",
    label: "Current agreement read",
    state: "available",
    owner: "Member A",
    endpoint: "GET /api/v1/deals/{id}/workspace",
    note: "Served inside the workspace snapshot with dual-consent timestamps and the hash.",
  },
  {
    id: "negotiate-step",
    label: "Bounded agent counteroffer",
    state: "pending",
    owner: "Member A / C",
    endpoint: "POST /api/v1/deals/{id}/negotiate-step",
    note: "The UI does not invent an agent offer when the endpoint is absent.",
  },
  {
    id: "evidence",
    label: "Evidence upload and listing",
    state: "available",
    owner: "Member E / D",
    endpoint: "POST /api/v1/deals/{id}/evidence",
    note: "Upload via multipart; listing comes from the workspace snapshot with version, hash, and signed URL.",
  },
  {
    id: "disputes",
    label: "Open a dispute",
    state: "available",
    owner: "Member E",
    endpoint: "POST /api/v1/deals/{id}/disputes",
    note: "Opening a dispute transitions the deal to DISPUTED and freezes automated capture.",
  },
  {
    id: "audit",
    label: "Audit timeline and WebSocket replay",
    state: "pending",
    owner: "Member E",
    endpoint: "GET /api/v1/deals/{id}/audit, WSS /ws/deals/{id}",
    note: "Live mode shows no timeline events until the backend exposes them.",
  },
];
