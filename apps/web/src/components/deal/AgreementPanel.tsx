"use client";

import { useState } from "react";
import type { AgreementSnapshotDTO, DealWorkspaceDTO, StructuredTermsDTO } from "@/types/contracts";
import { defaultOfferTerms } from "@/lib/deal-defaults";
import { draftFromTerms, termsFromDraft, type FieldErrors, type TermsDraft } from "@/lib/forms";
import { formatDateTime, formatMinorUnits, pluralize } from "@/lib/format";
import { useMockData } from "@/providers/MockDataProvider";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { TermsFields } from "@/components/forms/TermsFields";
import styles from "./AgreementPanel.module.css";

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

function AgreementChanges({ current, previous }: { current: StructuredTermsDTO; previous?: StructuredTermsDTO }) {
  if (!previous) return <p className="mt-3 text-sm text-slate-600">No earlier version to compare.</p>;

  const fields = [
    { label: "Price", before: formatMinorUnits(previous.priceMinorUnits, previous.currency), after: formatMinorUnits(current.priceMinorUnits, current.currency) },
    { label: "Delivery", before: pluralize(previous.deliveryDays, "day"), after: pluralize(current.deliveryDays, "day") },
    { label: "Revisions", before: String(previous.includedRevisions), after: String(current.includedRevisions) },
    { label: "Project", before: previous.title, after: current.title },
    { label: "Deliverables", before: previous.deliverables.join("\n"), after: current.deliverables.join("\n") },
    { label: "Acceptance criteria", before: previous.acceptanceCriteriaText, after: current.acceptanceCriteriaText },
  ].filter(({ before, after }) => before !== after);

  return fields.length ? (
    <dl className="mt-3 divide-y divide-indigo-950/15">
      {fields.map(({ label, before, after }) => (
        <div key={label} className="py-3 text-sm">
          <dt className="font-medium text-slate-600">{label}</dt>
          <dd className="mt-1 break-words whitespace-pre-wrap"><span className="text-slate-600"><span className="font-medium text-rose-800">From</span> {before}</span><br /><strong className="text-emerald-800">To {after}</strong></dd>
        </div>
      ))}
    </dl>
  ) : <p className="mt-3 text-sm text-slate-600">The terms are unchanged.</p>;
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
      onDone(`Agreement v${agreement.version} is ready for review.`);
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
        Both parties will need to accept the revised terms.
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
  previousTerms,
  onOpenSettlement,
}: {
  workspace: DealWorkspaceDTO;
  previousTerms?: StructuredTermsDTO;
  onOpenSettlement: () => void;
}) {
  const { actions } = useMockData();
  const [feedback, setFeedback] = useState<{ tone: "success" | "error" | "info"; message: string } | null>(null);
  const [editing, setEditing] = useState(false);
  const [confirmedAgreementId, setConfirmedAgreementId] = useState<string | null>(null);
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
    if (!agreement || !canAccept || confirmedAgreementId !== agreement.agreementId || accepting) return;
    setAccepting(true);
    setFeedback(null);
    try {
      const updated = await actions.acceptAgreement(workspace.dealId, agreement.version, agreement.termsHashSha256);
      const complete = Boolean(updated.buyerAcceptedAt && updated.sellerAcceptedAt);
      setFeedback({
        tone: "success",
        message: complete
          ? `Agreement v${updated.version} was accepted by both parties.`
          : `Agreement v${updated.version} accepted. Waiting for the other party.`,
      });
      setConfirmedAgreementId(null);
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
            title="Agreement pending"
            description="Prepare the terms for buyer and seller review."
          />
          <CardBody>
            {workspace.offers.length === 0 ? (
              <EmptyState
                title="Negotiate an offer first"
                description="Draft the scope, price, and delivery terms to start the agreement."
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
        <div className={styles.layout}>
          <section className={styles.paper} aria-labelledby="agreement-title">
            <div aria-hidden="true" className={styles.ribbon} />
            <div className="relative space-y-5">
              <div>
                <h2 id="agreement-title" className="font-serif text-2xl italic">Agreement v{agreement.version}</h2>
                <p className={styles.amount}>{formatMinorUnits(agreement.canonicalTerms.priceMinorUnits, agreement.canonicalTerms.currency)}</p>
                <p className="text-sm font-medium text-slate-600">{agreement.canonicalTerms.currency}</p>
              </div>
              <dl className="grid grid-cols-2 gap-4 border-y border-indigo-950/15 py-4">
                <div><dt className="text-sm text-slate-600">Delivery</dt><dd className="font-serif text-2xl">{pluralize(agreement.canonicalTerms.deliveryDays, "day")}</dd></div>
                <div><dt className="text-sm text-slate-600">Included revisions</dt><dd className="font-serif text-2xl">{agreement.canonicalTerms.includedRevisions}</dd></div>
              </dl>
              <details className="space-y-3 border-t border-indigo-950/15 pt-3 text-sm">
                <summary className="cursor-pointer font-medium">View full terms</summary>
                <div>
                  <h3 className="font-semibold">Deliverables</h3>
                  <ul className="mt-1 list-disc space-y-1 pl-5">{agreement.canonicalTerms.deliverables.map((item, index) => <li key={index}>{item}</li>)}</ul>
                </div>
                <div>
                  <h3 className="font-semibold">Acceptance criteria</h3>
                  <p className="mt-1 whitespace-pre-wrap">{agreement.canonicalTerms.acceptanceCriteriaText}</p>
                </div>
              </details>
              <details className="border-t border-indigo-950/15 pt-3 text-sm">
                <summary className="cursor-pointer font-medium">Agreement fingerprint (SHA-256)</summary>
                <code className="my-2 block break-all text-xs">{agreement.termsHashSha256}</code>
                <CopyButton value={agreement.termsHashSha256} label="Copy fingerprint" />
              </details>
            <div className="grid gap-2">
              <AcceptanceCard role="Buyer" acceptedAt={agreement.buyerAcceptedAt} isYou={workspace.myRole === "BUYER"} />
              <AcceptanceCard role="Seller" acceptedAt={agreement.sellerAcceptedAt} isYou={workspace.myRole === "SELLER"} />
            </div>

            {bothAccepted ? (
              <Alert
                tone="success"
                title="Accepted by both parties"
                action={
                  <Button size="sm" onClick={onOpenSettlement}>
                    Open settlement
                  </Button>
                }
              >
                Version {agreement.version} is ready for settlement.
              </Alert>
            ) : myAcceptedAt ? (
              <Alert tone="info" title="Your acceptance is recorded">
                Waiting for the other party to accept this version.
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
                    checked={confirmedAgreementId === agreement.agreementId}
                    onChange={(event) => setConfirmedAgreementId(event.target.checked ? agreement.agreementId : null)}
                    className="mt-0.5 h-4 w-4 rounded border-emerald-400 text-emerald-700 focus:ring-emerald-600"
                  />
                  I have reviewed the terms and accept this version.
                </label>
                <Button className={styles.accept} onClick={accept} loading={accepting} disabled={confirmedAgreementId !== agreement.agreementId}>
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
                      Revising the terms requires fresh approval from both parties.
                    </p>
                    <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
                      Propose a new version
                    </Button>
                  </div>
                )}
              </div>
            ) : null}
            </div>
          </section>
          <aside className={styles.changes} aria-label="Agreement changes">
            <h2 className="font-serif text-3xl tracking-tight">What changed</h2>
            <AgreementChanges current={agreement.canonicalTerms} previous={previousTerms} />
            <div className="mt-6 border-t border-indigo-950/15 pt-4">
              <h3 className="font-serif text-xl">Consent for v{agreement.version}</h3>
              <p className="mt-2 text-sm text-slate-600">
                {frozen ? "Consent is frozen during the dispute." : bothAccepted ? "Both parties accepted." : myAcceptedAt ? "Waiting for the other party." : "Your review is needed."}
              </p>
            </div>
          </aside>
        </div>
      )}
    </div>
  );
}
