"use client";

import { useState } from "react";
import type { AgreementSnapshotDTO, DealWorkspaceDTO } from "@/types/contracts";
import { defaultOfferTerms } from "@/lib/deal-defaults";
import { draftFromTerms, termsFromDraft, type FieldErrors, type TermsDraft } from "@/lib/forms";
import { formatDateTime } from "@/lib/format";
import { useMockData } from "@/providers/MockDataProvider";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { TermsFields } from "@/components/forms/TermsFields";
import { TermsSummary } from "./TermsSummary";

function AcceptanceCard({
  role,
  acceptedAt,
  isYou,
}: {
  role: "Buyer" | "Seller";
  acceptedAt: string | null;
  isYou: boolean;
}) {
  return (
    <div className={`rounded-xl border px-4 py-3 ${acceptedAt ? "border-emerald-200 bg-emerald-50/60" : "border-slate-200 bg-slate-50"}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-slate-900">
          {role}
          {isYou ? " (you)" : ""}
        </p>
        <Badge tone={acceptedAt ? "success" : "warning"}>{acceptedAt ? "Accepted" : "Waiting"}</Badge>
      </div>
      <p className="mt-1 text-xs text-slate-600">
        {acceptedAt ? `Signed ${formatDateTime(acceptedAt)}` : "No acceptance recorded for this version."}
      </p>
    </div>
  );
}

function ProposalForm({
  workspace,
  initial,
  onDone,
  onCancel,
}: {
  workspace: DealWorkspaceDTO;
  initial: AgreementSnapshotDTO["canonicalTerms"];
  onDone: (message: string) => void;
  onCancel: () => void;
}) {
  const { actions } = useMockData();
  const [draft, setDraft] = useState<TermsDraft>(() => draftFromTerms(initial));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const parsed = termsFromDraft(draft);
    setErrors(parsed.errors);
    if (!parsed.value) {
      setError("Review the highlighted terms before proposing the agreement.");
      return;
    }
    setSubmitting(true);
    try {
      const agreement = await actions.proposeAgreement(workspace.dealId, parsed.value);
      onDone(`Agreement v${agreement.version} proposed with a local demo hash.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The agreement could not be proposed.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4" noValidate>
      {error ? <Alert tone="error">{error}</Alert> : null}
      <Alert tone="warning" title="A new version clears prior acceptances">
        The agreement hash is bound to these exact terms. In demo mode the previous acceptances are cleared when a new
        version is proposed. The API remains the authority for the real RFC-8785 hash.
      </Alert>
      <TermsFields draft={draft} onChange={setDraft} errors={errors} idPrefix={`agreement-${workspace.dealId}`} />
      <div className="flex flex-wrap gap-2">
        <Button type="submit" loading={submitting}>
          Propose agreement
        </Button>
        <Button variant="secondary" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

export function AgreementPanel({
  workspace,
  onOpenSettlement,
}: {
  workspace: DealWorkspaceDTO;
  onOpenSettlement: () => void;
}) {
  const { actions } = useMockData();
  const [feedback, setFeedback] = useState<{ tone: "success" | "error" | "info"; message: string } | null>(null);
  const [editing, setEditing] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [accepting, setAccepting] = useState(false);

  const agreement = workspace.currentAgreement;
  const frozen = workspace.status === "DISPUTED" || workspace.disputeStatus === "OPEN";
  const myAcceptedAt = agreement
    ? workspace.myRole === "BUYER"
      ? agreement.buyerAcceptedAt
      : agreement.sellerAcceptedAt
    : null;
  const bothAccepted = Boolean(agreement?.buyerAcceptedAt && agreement?.sellerAcceptedAt);
  const canPropose = !frozen && (workspace.status === "ACTIVE_NEGOTIATION" || workspace.status === "TERMS_PENDING_ACCEPTANCE");
  const canAccept = Boolean(
    agreement &&
      !frozen &&
      workspace.status === "TERMS_PENDING_ACCEPTANCE" &&
      !myAcceptedAt &&
      !bothAccepted,
  );
  const initialTerms = agreement?.canonicalTerms ?? defaultOfferTerms(workspace);

  async function accept() {
    if (!agreement) return;
    setAccepting(true);
    setFeedback(null);
    try {
      const updated = await actions.acceptAgreement(workspace.dealId, agreement.version, agreement.termsHashSha256);
      const complete = Boolean(updated.buyerAcceptedAt && updated.sellerAcceptedAt);
      setFeedback({
        tone: "success",
        message: complete
          ? `Agreement v${updated.version} now has dual consent. The deal is ready for sandbox settlement.`
          : `Agreement v${updated.version} accepted in the demo workspace. The other party still needs to accept.`,
      });
      setConfirmed(false);
    } catch (error) {
      setFeedback({ tone: "error", message: error instanceof Error ? error.message : "Acceptance failed." });
    } finally {
      setAccepting(false);
    }
  }

  return (
    <div className="space-y-4">
      {frozen ? (
        <Alert tone="error" title="Agreement actions frozen">
          An open dispute blocks acceptance and settlement actions. The last recorded agreement stays visible for review.
        </Alert>
      ) : null}
      {feedback ? <Alert tone={feedback.tone}>{feedback.message}</Alert> : null}

      {!agreement ? (
        <Card>
          <CardHeader
            title="No agreement candidate yet"
            description="Propose the exact terms both parties will review. The hash binds the acceptance to one immutable version."
          />
          <CardBody>
            {workspace.offers.length === 0 ? (
              <EmptyState
                title="Negotiate an offer first"
                description="The agreement normally starts from a negotiated offer. You can still draft terms here in the demo, but the offer history is empty."
                action={
                  <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
                    Draft agreement terms
                  </Button>
                }
              />
            ) : null}
            {workspace.offers.length > 0 || editing ? (
              <ProposalForm
                key={`proposal-${workspace.offers.length}`}
                workspace={workspace}
                initial={initialTerms}
                onDone={(message) => {
                  setEditing(false);
                  setFeedback({ tone: "success", message });
                }}
                onCancel={() => setEditing(false)}
              />
            ) : null}
          </CardBody>
        </Card>
      ) : (
        <Card>
          <CardHeader
            title={`Agreement v${agreement.version}`}
            description="Both parties must accept this identical version and hash."
            action={
              <div className="flex flex-wrap gap-1.5">
                <Badge tone="info">Version {agreement.version}</Badge>
                {bothAccepted ? <Badge tone="success">Dual consent complete</Badge> : <Badge tone="warning">Dual consent pending</Badge>}
              </div>
            }
          />
          <CardBody className="space-y-5">
            <TermsSummary terms={agreement.canonicalTerms} />

            <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Agreement hash</p>
                  <code className="mt-1 block break-all font-mono text-[11px] leading-relaxed text-slate-700">
                    {agreement.termsHashSha256}
                  </code>
                </div>
                <CopyButton value={agreement.termsHashSha256} label="Copy hash" />
              </div>
              <p className="mt-2 text-xs leading-relaxed text-slate-500">
                Demo workspace: this is a local SHA-256 demo hash, not the server&apos;s RFC-8785 canonical hash. In live
                mode the value comes from the API response and is never recomputed for authority.
              </p>
            </div>

            <div className="grid gap-2 sm:grid-cols-2">
              <AcceptanceCard role="Buyer" acceptedAt={agreement.buyerAcceptedAt} isYou={workspace.myRole === "BUYER"} />
              <AcceptanceCard role="Seller" acceptedAt={agreement.sellerAcceptedAt} isYou={workspace.myRole === "SELLER"} />
            </div>

            {bothAccepted ? (
              <Alert
                tone="success"
                title="The agreement has dual consent"
                action={
                  <Button size="sm" onClick={onOpenSettlement}>
                    Open settlement
                  </Button>
                }
              >
                Both acceptance timestamps were recorded against version {agreement.version}. The next server-verified step is
                sandbox settlement.
              </Alert>
            ) : myAcceptedAt ? (
              <Alert tone="info" title="Your acceptance is recorded">
                Waiting for the other party to accept this exact version and hash.
              </Alert>
            ) : canAccept ? (
              <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 px-4 py-4">
                <p className="text-sm font-semibold text-emerald-950">Accept agreement v{agreement.version}</p>
                <p className="mt-1 text-xs leading-relaxed text-emerald-900/80">
                  You are accepting the exact terms above. Any later edit creates a new version and invalidates this
                  acceptance.
                </p>
                <label className="mt-3 flex items-start gap-2 text-xs font-medium text-emerald-950">
                  <input
                    type="checkbox"
                    checked={confirmed}
                    onChange={(event) => setConfirmed(event.target.checked)}
                    className="mt-0.5 h-4 w-4 rounded border-emerald-400 text-emerald-700 focus:ring-emerald-600"
                  />
                  I reviewed the terms and hash, and I accept this version.
                </label>
                <Button className="mt-3" onClick={accept} loading={accepting} disabled={!confirmed}>
                  Accept agreement v{agreement.version}
                </Button>
              </div>
            ) : (
              <Alert tone="warning">
                Acceptance is unavailable while the deal is{" "}
                {workspace.status.replaceAll("_", " ").toLowerCase()}.
              </Alert>
            )}

            {canPropose ? (
              <div className="border-t border-slate-200 pt-4">
                {editing ? (
                  <ProposalForm
                    key={`proposal-revise-${agreement.version}`}
                    workspace={workspace}
                    initial={agreement.canonicalTerms}
                    onDone={(message) => {
                      setEditing(false);
                      setFeedback({ tone: "success", message });
                    }}
                    onCancel={() => setEditing(false)}
                  />
                ) : (
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-xs leading-relaxed text-slate-500">
                      Need different terms? A new version invalidates both prior acceptances.
                    </p>
                    <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
                      Propose a new version
                    </Button>
                  </div>
                )}
              </div>
            ) : null}
          </CardBody>
        </Card>
      )}
    </div>
  );
}
