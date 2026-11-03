"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import type {
  AgreementSnapshotDTO,
  DealStatus,
  DealWorkspaceDTO,
  MandateDTO,
  OfferDTO,
  PartyRole,
  SettlementStateDTO,
  StructuredTermsDTO,
} from "@/types/contracts";
import { api, ApiError, ApiNetworkError } from "@/lib/api/client";
import { LIVE_CAPABILITIES } from "@/lib/api/capabilities";
import { draftFromTerms, termsFromDraft, type FieldErrors, type TermsDraft } from "@/lib/forms";
import { formatMinorUnits, shortenHash } from "@/lib/format";
import { readLiveDealSession, writeLiveDealSession, type LiveDealSession } from "@/lib/live-session";
import { useSession } from "@/providers/SessionProvider";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { Field, Input, Textarea } from "@/components/ui/Field";
import { MandateBuilderForm } from "@/components/forms/MandateBuilderForm";
import { OfferForm } from "@/components/forms/OfferForm";
import { TermsFields } from "@/components/forms/TermsFields";
import { DealStatusBadge, InvoiceStatusBadge, PaymentStatusBadge } from "./StatusBadges";
import { MandateSummary } from "./MandateSummary";
import { OfferComparison } from "./OfferComparison";
import { TermsSummary } from "./TermsSummary";

function messageFor(error: unknown): string {
  if (error instanceof ApiNetworkError || error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return "The request failed.";
}

function liveDefaultTerms(
  terms: StructuredTermsDTO | undefined,
  agreement: AgreementSnapshotDTO | null,
  offers: OfferDTO[],
  settlement: SettlementStateDTO | null,
): StructuredTermsDTO {
  if (terms) return terms;
  if (agreement) return agreement.canonicalTerms;
  const latestOffer = offers[offers.length - 1];
  if (latestOffer) return latestOffer.terms;
  return {
    title: "",
    deliverables: [],
    priceMinorUnits: settlement?.amountMinorUnits ?? 0,
    currency: settlement?.currency ?? "USD",
    deliveryDays: 14,
    includedRevisions: 1,
    acceptanceCriteriaText: "",
  };
}

function deriveStatus(
  agreement: AgreementSnapshotDTO | null,
  settlement: SettlementStateDTO | null,
): DealStatus | null {
  if (settlement?.paymentStatus === "COMPLETED") return "CLOSED";
  if (settlement?.paymentStatus === "VOIDED") return "CANCELLED";
  if (settlement?.paymentStatus === "AUTHORIZED") return "IN_PROGRESS";
  if (settlement?.paymentStatus === "ORDER_CREATED") return "SETTLEMENT_PENDING";
  if (agreement?.buyerAcceptedAt && agreement?.sellerAcceptedAt) return "AGREED";
  if (agreement) return "TERMS_PENDING_ACCEPTANCE";
  return null;
}

function LiveAgreementProposal({
  dealId,
  token,
  initial,
  disabled,
  onProposed,
}: {
  dealId: string;
  token: string;
  initial: StructuredTermsDTO;
  disabled: boolean;
  onProposed: (agreement: AgreementSnapshotDTO) => void;
}) {
  const [draft, setDraft] = useState<TermsDraft>(() => draftFromTerms(initial));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const parsed = termsFromDraft(draft);
    setErrors(parsed.errors);
    if (!parsed.value) {
      setError("Review the highlighted terms.");
      return;
    }
    setBusy(true);
    try {
      const agreement = await api.proposeAgreement(dealId, token, parsed.value);
      onProposed(agreement);
    } catch (caught) {
      setError(messageFor(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      {error ? <Alert tone="error">{error}</Alert> : null}
      <TermsFields draft={draft} onChange={setDraft} errors={errors} idPrefix={`live-agreement-${dealId}`} disabled={disabled} />
      <Button type="submit" loading={busy} disabled={disabled}>
        Propose agreement to the API
      </Button>
    </form>
  );
}

export function LiveDealConsole({ dealId }: { dealId: string }) {
  const { liveSession, apiBaseUrl, ready } = useSession();
  const token = liveSession?.token ?? null;

  const [sessionState, setSessionState] = useState<LiveDealSession | null>(null);
  const [workspace, setWorkspace] = useState<DealWorkspaceDTO | null>(null);
  const [mandate, setMandate] = useState<MandateDTO | null>(null);
  const [settlement, setSettlement] = useState<SettlementStateDTO | null>(null);
  const [agreement, setAgreement] = useState<AgreementSnapshotDTO | null>(null);
  const [offers, setOffers] = useState<OfferDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ tone: "success" | "error" | "info" | "warning"; message: string } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [editingMandate, setEditingMandate] = useState(false);
  const [voidReason, setVoidReason] = useState("Buyer requested a void from the live console.");
  const [joinToken, setJoinToken] = useState("");
  const [joinError, setJoinError] = useState<string | null>(null);
  const [health, setHealth] = useState<"checking" | "up" | "down">("checking");

  const status = useMemo<DealStatus | null>(() => {
    const serverDerived = deriveStatus(agreement, settlement);
    return serverDerived ?? workspace?.status ?? sessionState?.statusHint ?? null;
  }, [agreement, sessionState?.statusHint, settlement, workspace?.status]);
  const role: PartyRole | null =
    mandate?.partyRole ?? workspace?.myRole ?? sessionState?.roleHint ?? null;
  const currentAgreement = agreement ?? sessionState?.currentAgreement ?? null;
  const terms = sessionState?.terms ?? currentAgreement?.canonicalTerms ?? offers[offers.length - 1]?.terms;
  const isBuyer = role === "BUYER";
  const isSeller = role === "SELLER";
  const frozen = status === "DISPUTED";
  const ownAccepted = currentAgreement
    ? role === "BUYER"
      ? currentAgreement.buyerAcceptedAt
      : currentAgreement.sellerAcceptedAt
    : null;
  const bothAccepted = Boolean(currentAgreement?.buyerAcceptedAt && currentAgreement?.sellerAcceptedAt);
  const statusGated = status === null;

  const updateSession = useCallback(
    (patch: Partial<LiveDealSession>) => {
      const next = writeLiveDealSession(dealId, patch);
      setSessionState(next);
    },
    [dealId],
  );

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setLoadError(null);

    let snapshot: DealWorkspaceDTO | null = null;
    try {
      snapshot = await api.getDealWorkspace(dealId, token);
    } catch (caught) {
      if (!(caught instanceof ApiError) || (caught.status !== 404 && caught.status !== 405)) {
        setLoadError(messageFor(caught));
        setLoading(false);
        return;
      }
    }

    if (snapshot) {
      setWorkspace(snapshot);
      setMandate(snapshot.myMandate);
      setSettlement(snapshot.settlement);
      setAgreement(snapshot.currentAgreement);
      setOffers(snapshot.offers);
      updateSession({ roleHint: snapshot.myRole });
      setLoading(false);
      return;
    }

    const [mandateResult, settlementResult] = await Promise.allSettled([
      api.getMandate(dealId, token),
      api.getSettlement(dealId, token),
    ]);

    let firstError: unknown = null;
    if (mandateResult.status === "fulfilled") {
      setMandate(mandateResult.value);
      updateSession({ roleHint: mandateResult.value.partyRole });
    } else {
      const reason = mandateResult.reason;
      if (!(reason instanceof ApiError) || (reason.status !== 404 && reason.status !== 500)) firstError = reason;
      setMandate(null);
    }
    if (settlementResult.status === "fulfilled") {
      setSettlement(settlementResult.value);
    } else {
      if (!firstError) firstError = settlementResult.reason;
      setSettlement(null);
    }
    if (firstError) setLoadError(messageFor(firstError));
    setLoading(false);
  }, [dealId, token, updateSession]);

  useEffect(() => {
    setSessionState(readLiveDealSession(dealId));
  }, [dealId]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    let active = true;
    api
      .health()
      .then(() => {
        if (active) setHealth("up");
      })
      .catch(() => {
        if (active) setHealth("down");
      });
    return () => {
      active = false;
    };
  }, []);

  async function saveMandate(input: Parameters<typeof api.createMandate>[2]) {
    if (!token) throw new Error("Sign in again.");
    const saved = await api.createMandate(dealId, token, input);
    setMandate(saved);
    updateSession({ roleHint: saved.partyRole });
    setEditingMandate(false);
    setFeedback({ tone: "success", message: `Mandate v${saved.version} saved by the API for the ${saved.partyRole.toLowerCase()} side.` });
  }

  async function submitOffer(nextTerms: StructuredTermsDTO, message: string) {
    if (!token || !role) throw new Error("Sign in again.");
    const offer = await api.createOffer(dealId, token, nextTerms, message);
    const nextOffers = [...offers, offer];
    setOffers(nextOffers);
    updateSession({ offers: nextOffers, statusHint: "ACTIVE_NEGOTIATION", terms: nextTerms });
    setFeedback({
      tone: "success",
      message: `The API accepted offer round ${offer.roundNumber}. Utility score: ${offer.callerUtilityScore}.`,
    });
  }

  async function acceptAgreement() {
    if (!token || !currentAgreement) return;
    setBusy("accept");
    setFeedback(null);
    try {
      const updated = await api.acceptAgreement(dealId, currentAgreement.version, currentAgreement.termsHashSha256, token);
      setAgreement(updated);
      const complete = Boolean(updated.buyerAcceptedAt && updated.sellerAcceptedAt);
      updateSession({
        currentAgreement: updated,
        statusHint: complete ? "AGREED" : "TERMS_PENDING_ACCEPTANCE",
      });
      setFeedback({
        tone: "success",
        message: complete
          ? "The API recorded both acceptances against the same hash. The deal is AGREED."
          : "The API recorded your acceptance. Waiting for the other party.",
      });
    } catch (caught) {
      setFeedback({ tone: "error", message: messageFor(caught) });
    } finally {
      setBusy(null);
    }
  }

  async function initiateSettlement() {
    if (!token) return;
    setBusy("initiate");
    setFeedback(null);
    try {
      const state = await api.initiateSettlement(dealId, token);
      setSettlement(state);
      updateSession({ statusHint: "SETTLEMENT_PENDING" });
      setFeedback({ tone: "success", message: "The API returned the settlement state after initiating the invoice and order." });
    } catch (caught) {
      setFeedback({ tone: "error", message: messageFor(caught) });
    } finally {
      setBusy(null);
    }
  }

  async function authorizeSettlement() {
    if (!token || !settlement?.paypalOrderId) return;
    setBusy("authorize");
    setFeedback(null);
    try {
      const state = await api.authorizeSettlement(dealId, settlement.paypalOrderId, token);
      setSettlement(state);
      updateSession({ statusHint: "IN_PROGRESS" });
      setFeedback({ tone: "success", message: "The API returned the authorization state. No browser PayPal call was made." });
    } catch (caught) {
      setFeedback({ tone: "error", message: messageFor(caught) });
    } finally {
      setBusy(null);
    }
  }

  async function voidSettlement() {
    if (!token) return;
    setBusy("void");
    setFeedback(null);
    try {
      const state = await api.voidSettlement(dealId, voidReason, token);
      setSettlement(state);
      updateSession({ statusHint: "CANCELLED" });
      setFeedback({ tone: "success", message: "The API returned the voided settlement state." });
    } catch (caught) {
      setFeedback({ tone: "error", message: messageFor(caught) });
    } finally {
      setBusy(null);
    }
  }

  async function join() {
    if (!token) return;
    setBusy("join");
    setJoinError(null);
    try {
      await api.joinDeal(dealId, joinToken, token);
      updateSession({ roleHint: "SELLER", inviteToken: joinToken, statusHint: "ACTIVE_NEGOTIATION" });
      setFeedback({ tone: "success", message: "The API accepted the join request. Reload the mandate to continue." });
      setJoinToken("");
      await load();
    } catch (caught) {
      setJoinError(messageFor(caught));
    } finally {
      setBusy(null);
    }
  }

  if (!ready) {
    return <Alert tone="info">Loading session…</Alert>;
  }

  if (!liveSession) {
    return (
      <div className="space-y-4">
        <Alert
          tone="warning"
          title="Sign in required"
          action={
            <Link href="/login" className="font-semibold underline underline-offset-2">
              Sign in
            </Link>
          }
        >
          The live console calls only {apiBaseUrl}. It cannot load a deal workspace without a JWT.
        </Alert>
      </div>
    );
  }

  const pendingForThisDeal = LIVE_CAPABILITIES.filter((item) => item.state === "pending");

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <Link href="/" className="text-xs font-semibold text-slate-500 underline underline-offset-2 hover:text-slate-900">
              ← Dashboard
            </Link>
            <Badge tone="info">Live API</Badge>
            {status ? <DealStatusBadge status={status} /> : <Badge tone="warning">Status unknown</Badge>}
            {role ? <Badge tone="accent">{role} view</Badge> : <Badge tone="neutral">Role not loaded</Badge>}
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">Live deal console</h1>
          <p className="break-all font-mono text-xs text-slate-500">{dealId}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <CopyButton value={dealId} label="Copy deal ID" />
          <Button variant="secondary" size="sm" onClick={() => void load()} loading={loading}>
            Refresh server state
          </Button>
        </div>
      </div>

      <Alert tone={health === "up" ? "success" : health === "down" ? "error" : "info"} title={`Backend: ${health}`}>
        Health check <code className="rounded bg-white/70 px-1">GET /api/v1/health</code> on {apiBaseUrl}. The browser calls
        only this API; PayPal Sandbox is reached by the backend.
      </Alert>

      {!workspace ? (
        <Alert tone="warning" title="Workspace snapshot endpoint pending">
          Member A has not published <code className="rounded bg-white/70 px-1">GET /api/v1/deals/{"{id}"}/workspace</code>.
          This console can show your mandate, settlement state, and actions you perform in this tab. It cannot load offer
          history, agreement history, evidence, dispute state, or status from the server. Actions stay gated by the
          server-confirmed state below; when the status is unknown, status-dependent actions are disabled.
        </Alert>
      ) : null}

      {workspace?.disputeStatus && workspace.disputeStatus !== "NONE" ? (
        <Alert
          tone={workspace.status === "DISPUTED" ? "error" : "warning"}
          title={`Dispute: ${workspace.disputeStatus.replaceAll("_", " ").toLowerCase()}`}
        >
          {workspace.disputeReason ?? "See dispute details in the deal record."}
        </Alert>
      ) : null}

      {loadError ? <Alert tone="error">{loadError}</Alert> : null}
      {feedback ? <Alert tone={feedback.tone}>{feedback.message}</Alert> : null}

      {sessionState?.terms ? (
        <Card>
          <CardHeader
            title="Server-returned intake terms"
            description="Saved from the intake response in this tab. Refreshing the page keeps this tab's session state."
          />
          <CardBody>
            <TermsSummary terms={sessionState.terms} compact />
            {sessionState.inviteToken ? (
              <div className="mt-3 border-t border-slate-200 pt-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Seller invite token</p>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <code className="break-all rounded bg-slate-100 px-2 py-1 text-xs">{sessionState.inviteToken}</code>
                  <CopyButton value={sessionState.inviteToken} />
                </div>
              </div>
            ) : null}
          </CardBody>
        </Card>
      ) : null}

      {!role ? (
        <Card>
          <CardHeader
            title="Role or mandate not loaded"
            description="The console knows your role after a successful intake, join, or mandate read. The server always derives the real role."
          />
          <CardBody className="space-y-4">
            <div className="flex flex-wrap items-center gap-3">
              <Button variant="secondary" onClick={() => void load()} loading={loading}>
                Retry mandate read
              </Button>
              <span className="text-xs text-slate-500">
                Signed in as {liveSession.email}. The server derives your role from deal membership.
              </span>
            </div>
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <p className="text-sm font-semibold text-slate-900">Seller joining this deal?</p>
              <p className="mt-1 text-xs leading-relaxed text-slate-600">
                Paste the private invite token. It is sent in the request body, never in the URL.
              </p>
              <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                <label htmlFor="live-invite-token" className="sr-only">
                  Invite token
                </label>
                <Input
                  id="live-invite-token"
                  value={joinToken}
                  onChange={(event) => setJoinToken(event.target.value)}
                  placeholder="Private invite token"
                  autoComplete="off"
                />
                <Button onClick={join} loading={busy === "join"} disabled={!joinToken.trim()}>
                  Join deal
                </Button>
              </div>
              {joinError ? <p className="mt-2 text-xs font-medium text-rose-700">{joinError}</p> : null}
            </div>
          </CardBody>
        </Card>
      ) : null}

      {role ? (
        <>
          {mandate ? (
            <>
              <MandateSummary
                mandate={mandate}
                editing={editingMandate}
                onEdit={() => setEditingMandate((value) => !value)}
              />
              {editingMandate ? (
                <Card>
                  <CardHeader title="Save a new mandate version" description="The API versions the mandate for your role." />
                  <CardBody>
                    <MandateBuilderForm
                      key={`live-mandate-${mandate.mandateId}`}
                      initial={mandate}
                      role={mandate.partyRole}
                      onSubmit={saveMandate}
                      submitLabel="Save mandate version"
                    />
                  </CardBody>
                </Card>
              ) : null}
            </>
          ) : (
            <Card>
              <CardHeader
                title="Lock your private mandate through the API"
                description="The form sends only your mandate to the authenticated endpoint."
              />
              <CardBody>
                <MandateBuilderForm
                  initial={null}
                  role={role}
                  onSubmit={saveMandate}
                  submitLabel={`Lock ${role.toLowerCase()} mandate`}
                />
              </CardBody>
            </Card>
          )}

          <Card>
            <CardHeader
              title="Submit an offer"
              description="The API validates the terms against your mandate and returns the accepted round."
            />
            <CardBody>
              {statusGated ? (
                <Alert tone="warning">
                  Offer submission is disabled because this tab does not have a server-confirmed negotiation status. Open
                  the console from intake or join, or refresh after a workspace endpoint exists.
                </Alert>
              ) : status !== "ACTIVE_NEGOTIATION" || frozen ? (
                <Alert tone="warning">
                  The current server-confirmed status is {(status ?? "unknown").replaceAll("_", " ").toLowerCase()}. Offers
                  are accepted only during active negotiation.
                </Alert>
              ) : (
                <OfferForm
                  key={`live-offer-${offers.length}`}
                  initialTerms={liveDefaultTerms(terms, currentAgreement, offers, settlement)}
                  mandate={mandate}
                  onSubmit={submitOffer}
                  submitLabel="Submit offer to API"
                />
              )}
            </CardBody>
          </Card>

          {offers.length ? (
            <Card>
              <CardHeader
                title={workspace ? "Offer history from the API" : "Offers submitted in this tab"}
                description={
                  workspace
                    ? "Loaded from the server workspace snapshot; new offers submitted here are appended."
                    : "There is no offer list endpoint yet, so only this session's responses are shown."
                }
              />
              <CardBody>
                <OfferComparison offers={offers} myRole={role} />
              </CardBody>
            </Card>
          ) : null}

          <Card>
            <CardHeader
              title="Agreement"
              description="Propose and accept using server-issued versions and hashes. No hash is computed in the browser."
            />
            <CardBody className="space-y-4">
              {currentAgreement ? (
                <>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap gap-1.5">
                      <Badge tone="info">Version {currentAgreement.version}</Badge>
                      {bothAccepted ? <Badge tone="success">Dual consent</Badge> : <Badge tone="warning">Pending</Badge>}
                    </div>
                    <CopyButton value={currentAgreement.termsHashSha256} label="Copy server hash" />
                  </div>
                  <TermsSummary terms={currentAgreement.canonicalTerms} compact />
                  <div className="rounded-lg bg-slate-100 px-3 py-2">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Server terms hash</p>
                    <code className="mt-1 block break-all font-mono text-[11px] text-slate-700">
                      {currentAgreement.termsHashSha256}
                    </code>
                  </div>
                  <div className="grid gap-2 sm:grid-cols-2">
                    <div className="rounded-lg border border-slate-200 px-3 py-2 text-xs">
                      <p className="font-semibold text-slate-800">Buyer</p>
                      <p className="text-slate-500">{currentAgreement.buyerAcceptedAt ? "Accepted" : "Waiting"}</p>
                    </div>
                    <div className="rounded-lg border border-slate-200 px-3 py-2 text-xs">
                      <p className="font-semibold text-slate-800">Seller</p>
                      <p className="text-slate-500">{currentAgreement.sellerAcceptedAt ? "Accepted" : "Waiting"}</p>
                    </div>
                  </div>
                  {bothAccepted ? (
                    <Alert tone="success">Both acceptances are recorded against this server hash.</Alert>
                  ) : ownAccepted ? (
                    <Alert tone="info">Your acceptance is recorded. Waiting for the counterparty.</Alert>
                  ) : status === "TERMS_PENDING_ACCEPTANCE" && !frozen ? (
                    <Button onClick={acceptAgreement} loading={busy === "accept"}>
                      Accept agreement v{currentAgreement.version} via API
                    </Button>
                  ) : (
                    <Alert tone="warning">
                      Acceptance is unavailable at the current server-confirmed status.
                    </Alert>
                  )}
                </>
              ) : (
                <LiveAgreementProposal
                  dealId={dealId}
                  token={liveSession.token}
                  initial={liveDefaultTerms(terms, currentAgreement, offers, settlement)}
                  disabled={statusGated || frozen}
                  onProposed={(next) => {
                    setAgreement(next);
                    updateSession({ currentAgreement: next, statusHint: "TERMS_PENDING_ACCEPTANCE", terms: next.canonicalTerms });
                    setFeedback({ tone: "success", message: `The API returned agreement v${next.version} and its hash.` });
                  }}
                />
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Settlement state"
              description="Read from GET /settlement. Actions call the backend; no PayPal request originates in the browser."
              action={
                settlement ? (
                  <div className="flex flex-wrap gap-1.5">
                    <InvoiceStatusBadge status={settlement.invoiceStatus} />
                    <PaymentStatusBadge status={settlement.paymentStatus} />
                  </div>
                ) : null
              }
            />
            <CardBody className="space-y-4">
              {settlement ? (
                <>
                  <p className="text-lg font-bold text-slate-900">
                    {formatMinorUnits(settlement.amountMinorUnits, settlement.currency)}
                  </p>
                  <dl className="grid gap-2 text-xs sm:grid-cols-2">
                    <div className="rounded-lg bg-slate-50 px-3 py-2">
                      <dt className="text-slate-500">Invoice ID</dt>
                      <dd className="font-mono text-slate-800">{settlement.paypalInvoiceId ?? "Not returned"}</dd>
                    </div>
                    <div className="rounded-lg bg-slate-50 px-3 py-2">
                      <dt className="text-slate-500">Order ID</dt>
                      <dd className="font-mono text-slate-800">{settlement.paypalOrderId ?? "Not returned"}</dd>
                    </div>
                    <div className="rounded-lg bg-slate-50 px-3 py-2">
                      <dt className="text-slate-500">Authorization ID</dt>
                      <dd className="font-mono text-slate-800">
                        {settlement.paypalAuthorizationId ? shortenHash(settlement.paypalAuthorizationId, 10) : "Not returned"}
                      </dd>
                    </div>
                    <div className="rounded-lg bg-slate-50 px-3 py-2">
                      <dt className="text-slate-500">Capture ID</dt>
                      <dd className="font-mono text-slate-800">{settlement.paypalCaptureId ?? "Not returned"}</dd>
                    </div>
                  </dl>
                  {settlement.paypalInvoiceViewUrl ? (
                    <a
                      href={settlement.paypalInvoiceViewUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-xs font-semibold text-sky-800 underline underline-offset-2"
                    >
                      Open sandbox invoice
                    </a>
                  ) : null}

                  <div className="flex flex-wrap gap-2 border-t border-slate-200 pt-3">
                    {isBuyer && status === "AGREED" ? (
                      <Button onClick={initiateSettlement} loading={busy === "initiate"}>
                        Initiate via API
                      </Button>
                    ) : null}
                    {isBuyer && settlement.paymentStatus === "ORDER_CREATED" ? (
                      <Button onClick={authorizeSettlement} loading={busy === "authorize"}>
                        Authorize hold via API
                      </Button>
                    ) : null}
                    {isBuyer && (settlement.paymentStatus === "ORDER_CREATED" || settlement.paymentStatus === "AUTHORIZED") ? (
                      <Button variant="danger" onClick={voidSettlement} loading={busy === "void"} disabled={!voidReason.trim()}>
                        Void via API
                      </Button>
                    ) : null}
                    {isSeller ? <p className="text-xs text-slate-500">Seller view is read-only for settlement.</p> : null}
                  </div>
                  {isBuyer && (settlement.paymentStatus === "ORDER_CREATED" || settlement.paymentStatus === "AUTHORIZED") ? (
                    <Field label="Void reason" htmlFor="live-void-reason">
                      <Textarea
                        id="live-void-reason"
                        rows={2}
                        value={voidReason}
                        onChange={(event) => setVoidReason(event.target.value)}
                      />
                    </Field>
                  ) : null}
                </>
              ) : (
                <Alert tone="info">
                  The settlement endpoint did not return state for this deal yet. It becomes available after agreement and
                  initiation; until then, no identifiers are displayed.
                </Alert>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardHeader
              title="Actions pending backend routes"
              description="These are deliberately disabled in live mode. The UI does not fabricate hashes, IDs, or events."
            />
            <CardBody>
              <div className="grid gap-2 md:grid-cols-2">
                {pendingForThisDeal.map((item) => (
                  <div key={item.id} className="rounded-lg border border-amber-200 bg-amber-50/60 px-3 py-2">
                    <div className="flex flex-wrap items-center justify-between gap-1">
                      <p className="text-xs font-semibold text-amber-950">{item.label}</p>
                      <Badge tone="warning">{item.owner}</Badge>
                    </div>
                    <p className="mt-0.5 font-mono text-[10px] text-amber-900/80">{item.endpoint}</p>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs leading-relaxed text-slate-500">
                In particular, accepting deliverables is unavailable because there is no evidence-list endpoint to verify a
                version, and opening a dispute is unavailable because the disputes route is not published.
              </p>
            </CardBody>
          </Card>
        </>
      ) : null}
    </div>
  );
}
