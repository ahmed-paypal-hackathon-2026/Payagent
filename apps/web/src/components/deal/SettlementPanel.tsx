"use client";

import { useState } from "react";
import type { DealWorkspaceDTO } from "@/types/contracts";
import { formatDateTime, formatMinorUnits, shortenHash } from "@/lib/format";
import { useMockData } from "@/providers/MockDataProvider";
import { useSession } from "@/providers/SessionProvider";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Textarea } from "@/components/ui/Field";
import { InvoiceStatusBadge, PaymentStatusBadge } from "./StatusBadges";

function RailRow({ label, value, mono = false }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-slate-100 py-1.5 last:border-0">
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className={`break-all text-xs ${mono ? "font-mono" : ""} ${value ? "text-slate-800" : "text-slate-400"}`}>
        {value || "Not returned by the API"}
      </dd>
    </div>
  );
}

export function SettlementPanel({
  workspace,
  onOpenDeliverables,
}: {
  workspace: DealWorkspaceDTO;
  onOpenDeliverables: () => void;
}) {
  const { actions } = useMockData();
  const { mode } = useSession();
  const [feedback, setFeedback] = useState<{ tone: "success" | "error" | "info"; message: string } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [voidReason, setVoidReason] = useState("Buyer requested a void from the workspace.");

  const settlement = workspace.settlement;
  const frozen = workspace.status === "DISPUTED" || workspace.disputeStatus === "OPEN";
  const isBuyer = workspace.myRole === "BUYER";
  const canInitiate = isBuyer && !frozen && workspace.status === "AGREED" && settlement.invoiceStatus === "NOT_CREATED";
  const canAuthorize = isBuyer && !frozen && settlement.paymentStatus === "ORDER_CREATED";
  const canVoid =
    isBuyer && !frozen && (settlement.paymentStatus === "ORDER_CREATED" || settlement.paymentStatus === "AUTHORIZED");
  const showDeliverablesCta =
    workspace.status === "IN_PROGRESS" || workspace.status === "DELIVERABLES_SUBMITTED" || workspace.status === "CLOSED";

  async function run(key: string, action: () => Promise<void>, successMessage: string) {
    setBusy(key);
    setFeedback(null);
    try {
      await action();
      setFeedback({ tone: "success", message: successMessage });
    } catch (error) {
      setFeedback({ tone: "error", message: error instanceof Error ? error.message : "The settlement action failed." });
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="space-y-4">
      {frozen ? (
        <Alert tone="error" title="Capture is frozen">
          The deal is DISPUTED. Automated capture and acceptance are blocked; only a backend dispute resolution can
          change the freeze.
        </Alert>
      ) : null}
      {feedback ? <Alert tone={feedback.tone}>{feedback.message}</Alert> : null}

      <Card>
        <CardHeader
          title="Dual-rail settlement"
          description="Commercial invoice and funds authorization are separate rails. Values below are rendered from the workspace settlement state."
          action={
            <div className="flex flex-wrap gap-1.5">
              <InvoiceStatusBadge status={settlement.invoiceStatus} />
              <PaymentStatusBadge status={settlement.paymentStatus} />
            </div>
          }
        />
        <CardBody className="space-y-4">
          <div className="flex flex-wrap items-baseline justify-between gap-2 rounded-xl bg-slate-900 px-4 py-3 text-white">
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-slate-300">Settlement amount</p>
              <p className="text-xl font-bold">{formatMinorUnits(settlement.amountMinorUnits, settlement.currency)}</p>
            </div>
            <Badge tone="neutral" className="border-white/20 bg-white/10 text-white">
              Agreement v{settlement.agreementVersion}
            </Badge>
          </div>

          <div className="grid gap-3 lg:grid-cols-2">
            <section className="rounded-xl border border-slate-200 p-4" aria-labelledby="invoice-rail-heading">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 id="invoice-rail-heading" className="text-sm font-semibold text-slate-900">
                  Invoice rail
                </h3>
                <InvoiceStatusBadge status={settlement.invoiceStatus} />
              </div>
              <p className="mt-1 text-xs leading-relaxed text-slate-500">
                PayPal Invoicing v2 commercial record. The browser never creates or sends it.
              </p>
              <dl className="mt-3">
                <RailRow label="Invoice ID" value={settlement.paypalInvoiceId} mono />
                <RailRow label="Invoice number" value={settlement.paypalInvoiceNumber} mono />
              </dl>
              {settlement.paypalInvoiceViewUrl ? (
                <a
                  href={settlement.paypalInvoiceViewUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-3 inline-flex text-xs font-semibold text-sky-800 underline underline-offset-2 hover:text-sky-950"
                >
                  {mode === "mock" ? "Open synthetic sandbox invoice" : "Open PayPal sandbox invoice"}
                </a>
              ) : (
                <p className="mt-3 text-xs text-slate-400">No invoice view URL has been returned.</p>
              )}
            </section>

            <section className="rounded-xl border border-slate-200 p-4" aria-labelledby="auth-rail-heading">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 id="auth-rail-heading" className="text-sm font-semibold text-slate-900">
                  Authorization rail
                </h3>
                <PaymentStatusBadge status={settlement.paymentStatus} />
              </div>
              <p className="mt-1 text-xs leading-relaxed text-slate-500">
                Orders v2 intent=AUTHORIZE, then capture or void. Identifiers appear only when the server returns them.
              </p>
              <dl className="mt-3">
                <RailRow label="Order ID" value={settlement.paypalOrderId} mono />
                <RailRow
                  label="Authorization ID"
                  value={
                    settlement.paypalAuthorizationId
                      ? shortenHash(settlement.paypalAuthorizationId, 10)
                      : null
                  }
                  mono
                />
                <RailRow label="Capture ID" value={settlement.paypalCaptureId} mono />
                <RailRow label="Hold expires" value={formatDateTime(settlement.authorizationExpiresAt)} />
              </dl>
            </section>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
            <p>Last reconciled: {formatDateTime(settlement.lastReconciledAt)}</p>
            {settlement.paymentStatus === "UNKNOWN" || settlement.invoiceStatus === "UNKNOWN" ? (
              <Badge tone="warning">Reconciliation required</Badge>
            ) : null}
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader
          title="Settlement actions"
          description={
            isBuyer
              ? "Buyer-controlled sandbox actions. Each button waits for a resolved demo action or a live API response."
              : "Seller view is read-only for settlement. The buyer initiates the invoice, authorizes the hold, and captures or voids."
          }
        />
        <CardBody className="space-y-4">
          {!isBuyer ? (
            <Alert tone="info">
              No settlement buttons are shown for the seller. The seller can still see server-confirmed invoice and payment
              identifiers.
            </Alert>
          ) : null}

          {isBuyer && workspace.status === "AGREED" && settlement.invoiceStatus === "NOT_CREATED" ? (
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <p className="text-sm font-semibold text-slate-900">Initiate settlement</p>
              <p className="mt-1 text-xs leading-relaxed text-slate-600">
                Demo mode creates synthetic invoice and order labels. Live mode calls the backend, which calls PayPal
                Sandbox. The UI does not contact PayPal.
              </p>
              <Button
                className="mt-3"
                loading={busy === "initiate"}
                disabled={!canInitiate}
                onClick={() =>
                  run("initiate", async () => {
                    await actions.initiateSettlement(workspace.dealId);
                  }, "Demo invoice and order created. The next step is authorization.")
                }
              >
                Initiate sandbox settlement
              </Button>
            </div>
          ) : null}

          {canAuthorize ? (
            <div className="rounded-xl border border-sky-200 bg-sky-50/60 p-4">
              <p className="text-sm font-semibold text-sky-950">Authorize the buyer hold</p>
              <p className="mt-1 text-xs leading-relaxed text-sky-900/80">
                The order <code className="rounded bg-white/70 px-1">{settlement.paypalOrderId}</code> is ready. In live
                mode the backend authorizes it against PayPal Sandbox and returns the authorization ID.
              </p>
              <Button
                className="mt-3"
                loading={busy === "authorize"}
                onClick={() =>
                  run("authorize", async () => {
                    await actions.authorizeSettlement(workspace.dealId);
                  }, "Authorization recorded. The deal is now in progress.")
                }
              >
                Authorize hold
              </Button>
            </div>
          ) : null}

          {canVoid ? (
            <div className="rounded-xl border border-rose-200 bg-rose-50/60 p-4">
              <p className="text-sm font-semibold text-rose-950">Void the open authorization</p>
              <p className="mt-1 text-xs leading-relaxed text-rose-900/80">
                Voiding cancels the commercial invoice and releases the hold in this demo. The backend decision remains
                authoritative.
              </p>
              <label htmlFor="void-reason" className="mt-3 block text-xs font-semibold text-rose-950">
                Void reason
              </label>
              <Textarea
                id="void-reason"
                rows={2}
                value={voidReason}
                onChange={(event) => setVoidReason(event.target.value)}
                className="mt-1 border-rose-200 focus:border-rose-600 focus:ring-rose-600/20"
              />
              <Button
                variant="danger"
                className="mt-3"
                loading={busy === "void"}
                disabled={!voidReason.trim()}
                onClick={() =>
                  run("void", async () => {
                    await actions.voidSettlement(workspace.dealId, voidReason);
                  }, "Authorization voided and the deal cancelled in the demo workspace.")
                }
              >
                Void authorization
              </Button>
            </div>
          ) : null}

          {isBuyer && !canInitiate && !canAuthorize && !canVoid && !frozen ? (
            <Alert tone="info">
              There is no settlement action available at this state. Required transitions are enforced by the backend.
            </Alert>
          ) : null}

          {showDeliverablesCta ? (
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-3">
              <p className="text-xs leading-relaxed text-slate-500">
                {workspace.status === "IN_PROGRESS"
                  ? "The hold is authorized. The seller can upload deliverables next."
                  : "Deliverables are the next review surface."}
              </p>
              <Button variant="secondary" size="sm" onClick={onOpenDeliverables}>
                Open deliverables
              </Button>
            </div>
          ) : null}

          <p className="text-xs leading-relaxed text-slate-500">
            No financial success is shown by inference. In live mode every identifier and status above must come from the
            backend response.
          </p>
        </CardBody>
      </Card>
    </div>
  );
}
