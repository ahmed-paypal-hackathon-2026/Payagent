"use client";

import { useRef, useState } from "react";
import type { DealWorkspaceDTO, DeliverableEvidenceDTO, SettlementStateDTO } from "@/types/contracts";
import { formatBytes, formatDateTime } from "@/lib/format";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { Field, Input, Textarea } from "@/components/ui/Field";
import { DisputeStatusBadge } from "./StatusBadges";

export interface DeliverablesActions {
  uploadEvidence(dealId: string, file: File): Promise<DeliverableEvidenceDTO>;
  acceptDeliverables(dealId: string, evidenceVersion: number): Promise<SettlementStateDTO>;
  openDispute(dealId: string, reason: string, resolutionRequest: string): Promise<DealWorkspaceDTO>;
}

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const ALLOWED_TYPES = ["application/pdf", "application/zip", "image/png", "image/jpeg", "text/plain"];

function EvidenceCard({ evidence, mock }: { evidence: DeliverableEvidenceDTO; mock: boolean }) {
  return (
    <article className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-slate-900">{evidence.filename}</p>
          <p className="mt-0.5 text-xs text-slate-500">
            {evidence.mimeType || "unknown type"} · {formatBytes(evidence.sizeBytes)} · uploaded{" "}
            {formatDateTime(evidence.uploadedAt)}
          </p>
        </div>
        <div className="flex flex-wrap gap-1.5">
          <Badge tone="info">v{evidence.version}</Badge>
          <Badge tone="neutral">{evidence.uploaderRole === "SELLER" ? "Seller upload" : "Buyer upload"}</Badge>
        </div>
      </div>

      <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">SHA-256</p>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <code className="break-all font-mono text-[11px] leading-relaxed text-slate-700">{evidence.sha256Hash}</code>
          <CopyButton value={evidence.sha256Hash} label="Copy hash" />
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {evidence.downloadSignedUrl ? (
          mock ? (
            <div className="text-xs leading-relaxed text-slate-500">
              Synthetic signed URL from the fixture (not served by the demo):{" "}
              <code className="break-all rounded bg-slate-100 px-1 text-[11px]">{evidence.downloadSignedUrl}</code>
            </div>
          ) : (
            <a
              href={evidence.downloadSignedUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-800 hover:bg-slate-50"
            >
              Download with signed URL
            </a>
          )
        ) : (
          <p className="text-xs text-slate-500">
            No signed URL was returned. In live mode the backend issues a short-lived URL after a membership check.
          </p>
        )}
      </div>
    </article>
  );
}

export function DeliverablesPanel({
  workspace,
  actions,
  mode,
}: {
  workspace: DealWorkspaceDTO;
  actions: DeliverablesActions;
  mode: "mock" | "live";
}) {
  const mock = mode === "mock";
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ tone: "success" | "error" | "info"; message: string } | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [acceptConfirmed, setAcceptConfirmed] = useState(false);
  const [disputeReason, setDisputeReason] = useState("");
  const [resolutionRequest, setResolutionRequest] = useState("");

  const frozen = workspace.status === "DISPUTED" || workspace.disputeStatus === "OPEN";
  const isSeller = workspace.myRole === "SELLER";
  const isBuyer = workspace.myRole === "BUYER";
  const canUpload =
    isSeller && !frozen && (workspace.status === "IN_PROGRESS" || workspace.status === "DELIVERABLES_SUBMITTED");
  const latestEvidence = [...workspace.evidenceList].sort((a, b) => b.version - a.version)[0] ?? null;
  const canReview =
    isBuyer &&
    !frozen &&
    workspace.status === "DELIVERABLES_SUBMITTED" &&
    workspace.disputeStatus === "NONE" &&
    Boolean(latestEvidence);

  function chooseFile(selected: File | null) {
    setFileError(null);
    setFeedback(null);
    if (!selected) {
      setFile(null);
      return;
    }
    if (selected.size <= 0 || selected.size > MAX_FILE_BYTES) {
      setFile(null);
      setFileError("Choose a file between 1 byte and 10 MB.");
      return;
    }
    if (!ALLOWED_TYPES.includes(selected.type)) {
      setFile(null);
      setFileError("The demo allowlist is PDF, ZIP, PNG, JPEG, and plain text.");
      return;
    }
    setFile(selected);
  }

  async function upload() {
    if (!file) return;
    setBusy("upload");
    setFeedback(null);
    try {
      const evidence = await actions.uploadEvidence(workspace.dealId, file);
      setFeedback({
        tone: "success",
        message: mock
          ? `Demo evidence v${evidence.version} recorded. SHA-256 ${evidence.sha256Hash} was computed locally for this rehearsal; the backend would issue the authoritative hash and signed URL in live mode.`
          : `The API recorded evidence v${evidence.version}. SHA-256 ${evidence.sha256Hash} is authoritative and the download URL on the card expires in 15 minutes.`,
      });
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = "";
    } catch (error) {
      setFeedback({ tone: "error", message: error instanceof Error ? error.message : "The evidence could not be added." });
    } finally {
      setBusy(null);
    }
  }

  async function accept() {
    if (!latestEvidence) return;
    setBusy("accept");
    setFeedback(null);
    try {
      await actions.acceptDeliverables(workspace.dealId, latestEvidence.version);
      setFeedback({
        tone: "success",
        message: mock
          ? `Demo acceptance recorded for evidence v${latestEvidence.version}. Capture and invoice payment were completed by the local engine.`
          : `The API accepted evidence v${latestEvidence.version}. The settlement state below reflects the authoritative invoice and payment status.`,
      });
      setAcceptConfirmed(false);
    } catch (error) {
      setFeedback({ tone: "error", message: error instanceof Error ? error.message : "Acceptance failed." });
    } finally {
      setBusy(null);
    }
  }

  async function dispute() {
    setBusy("dispute");
    setFeedback(null);
    try {
      await actions.openDispute(workspace.dealId, disputeReason, resolutionRequest);
      setFeedback({
        tone: "success",
        message: mock
          ? "Demo dispute opened. The deal is now DISPUTED and automated capture is frozen."
          : "The API opened the dispute. The deal is DISPUTED and automated capture is frozen until it is resolved.",
      });
      setDisputeReason("");
      setResolutionRequest("");
    } catch (error) {
      setFeedback({ tone: "error", message: error instanceof Error ? error.message : "The dispute could not be opened." });
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="space-y-4">
      {frozen ? (
        <Alert tone="error" title="Deliverable acceptance is frozen">
          The deal is DISPUTED. The evidence list remains visible, but the buyer cannot accept or capture. A backend
          dispute resolution is required to change this state.
        </Alert>
      ) : null}
      {feedback ? <Alert tone={feedback.tone}>{feedback.message}</Alert> : null}

      <Card>
        <CardHeader
          title="Deliverable evidence"
          description="Files are shown with version, size, uploader, SHA-256, and a signed URL when the backend provides one."
          action={<Badge tone={workspace.evidenceList.length ? "info" : "neutral"}>{workspace.evidenceList.length} versions</Badge>}
        />
        <CardBody>
          {workspace.evidenceList.length === 0 ? (
            <EmptyState
              title="No deliverables uploaded"
              description={
                isSeller
                  ? "Upload the deliverable once the work is ready for buyer review."
                  : "The seller has not uploaded deliverable evidence for this deal."
              }
            />
          ) : (
            <div className="space-y-3">
              {[...workspace.evidenceList]
                .sort((a, b) => b.version - a.version)
                .map((evidence) => (
                  <EvidenceCard key={evidence.evidenceId} evidence={evidence} mock={mock} />
                ))}
            </div>
          )}
        </CardBody>
      </Card>

      {isSeller ? (
        <Card>
          <CardHeader
            title="Upload a deliverable version"
            description="Max 10 MB. The backend computes the authoritative SHA-256 and issues a 15-minute signed URL."
          />
          <CardBody className="space-y-4">
            {!canUpload ? (
              <Alert tone="warning">
                {frozen
                  ? "Uploads are blocked while the dispute freeze is active."
                  : `Uploads are available while the deal is in progress or awaiting review. Current status: ${workspace.status
                      .replaceAll("_", " ")
                      .toLowerCase()}.`}
              </Alert>
            ) : null}
            <Field
              label="Evidence file"
              htmlFor="evidence-file"
              hint="Demo allowlist: PDF, ZIP, PNG, JPEG, plain text."
              error={fileError}
            >
              <Input
                id="evidence-file"
                ref={fileInputRef}
                type="file"
                accept={ALLOWED_TYPES.join(",")}
                onChange={(event) => chooseFile(event.target.files?.[0] ?? null)}
                disabled={!canUpload || busy === "upload"}
                aria-invalid={Boolean(fileError)}
              />
            </Field>
            <div className="flex flex-wrap items-center gap-3">
              <Button onClick={upload} loading={busy === "upload"} disabled={!canUpload || !file}>
                Upload evidence
              </Button>
              <p className="text-xs leading-relaxed text-slate-500">
                {mock
                  ? "Demo mode hashes the file in your browser and labels it as simulated. Live mode never fabricates a hash or URL."
                  : "Live mode sends the file to the API, which computes the authoritative SHA-256 and issues a 15-minute signed URL."}
              </p>
            </div>
          </CardBody>
        </Card>
      ) : null}

      {isBuyer ? (
        <Card>
          <CardHeader
            title="Buyer review"
            description="Accept the latest evidence version to release capture, or open a dispute to freeze settlement."
          />
          <CardBody className="space-y-4">
            {workspace.disputeStatus !== "NONE" ? (
              <div className="rounded-xl border border-rose-200 bg-rose-50 p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm font-semibold text-rose-950">Dispute is {workspace.disputeStatus.replaceAll("_", " ").toLowerCase()}</p>
                  <DisputeStatusBadge status={workspace.disputeStatus} />
                </div>
                <p className="mt-2 text-xs leading-relaxed text-rose-900/80">{workspace.disputeReason}</p>
                <p className="mt-2 text-xs leading-relaxed text-rose-900/80">
                  Resolution is owned by the backend dispute workflow. This UI does not invent a resolution or release
                  capture while the freeze is active.
                </p>
              </div>
            ) : canReview && latestEvidence ? (
              <>
                <div className="rounded-xl border border-emerald-200 bg-emerald-50/60 p-4">
                  <p className="text-sm font-semibold text-emerald-950">
                    Accept evidence v{latestEvidence.version} and release capture
                  </p>
                  <p className="mt-1 text-xs leading-relaxed text-emerald-900/80">
                    {mock
                      ? "This demo action marks the invoice paid, completes the payment, and closes the deal locally. In live mode the success state must come from the backend."
                      : "This calls the acceptance endpoint; the response is the authoritative settlement state showing invoice and payment status."}
                  </p>
                  <label className="mt-3 flex items-start gap-2 text-xs font-medium text-emerald-950">
                    <input
                      type="checkbox"
                      checked={acceptConfirmed}
                      onChange={(event) => setAcceptConfirmed(event.target.checked)}
                      className="mt-0.5 h-4 w-4 rounded border-emerald-400 text-emerald-700 focus:ring-emerald-600"
                    />
                    I reviewed evidence v{latestEvidence.version} and the acceptance criteria.
                  </label>
                  <Button
                    variant="success"
                    className="mt-3"
                    loading={busy === "accept"}
                    disabled={!acceptConfirmed}
                    onClick={accept}
                  >
                    Accept deliverables &amp; release capture
                  </Button>
                </div>

                <div className="rounded-xl border border-rose-200 bg-rose-50/50 p-4">
                  <p className="text-sm font-semibold text-rose-950">Open a dispute instead</p>
                  <p className="mt-1 text-xs leading-relaxed text-rose-900/80">
                    A dispute immediately transitions the deal to DISPUTED and blocks automated capture.
                  </p>
                  <div className="mt-3 space-y-3">
                    <Field label="Reason" htmlFor="dispute-reason" required>
                      <Textarea
                        id="dispute-reason"
                        rows={3}
                        value={disputeReason}
                        onChange={(event) => setDisputeReason(event.target.value)}
                        placeholder="Explain which acceptance criteria are not met."
                      />
                    </Field>
                    <Field
                      label="Requested resolution"
                      htmlFor="dispute-resolution"
                      hint={
                        mock
                          ? "Shared with the dispute workflow; this demo stores it in the audit event."
                          : "Shared with the dispute workflow and stored with the dispute record."
                      }
                    >
                      <Textarea
                        id="dispute-resolution"
                        rows={2}
                        value={resolutionRequest}
                        onChange={(event) => setResolutionRequest(event.target.value)}
                        placeholder="Describe the requested correction or outcome."
                      />
                    </Field>
                    <Button variant="danger" loading={busy === "dispute"} disabled={!disputeReason.trim()} onClick={dispute}>
                      Open dispute &amp; freeze capture
                    </Button>
                  </div>
                </div>
              </>
            ) : (
              <Alert tone="info">
                {workspace.status === "DELIVERABLES_SUBMITTED"
                  ? "No evidence version is available to accept."
                  : `Buyer review unlocks when deliverables are submitted. Current status: ${workspace.status
                      .replaceAll("_", " ")
                      .toLowerCase()}.`}
              </Alert>
            )}
          </CardBody>
        </Card>
      ) : null}
    </div>
  );
}
