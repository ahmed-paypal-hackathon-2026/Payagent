"use client";

import Link from "next/link";
import { useState } from "react";
import type { DealStatus, DealWorkspaceDTO } from "@/types/contracts";
import { cn } from "@/lib/format";
import { useMockData } from "@/providers/MockDataProvider";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { AgreementPanel } from "./AgreementPanel";
import { AuditPanel } from "./AuditPanel";
import { DeliverablesPanel } from "./DeliverablesPanel";
import { NegotiationPanel } from "./NegotiationPanel";
import { SettlementPanel } from "./SettlementPanel";
import { DealStatusBadge, DisputeStatusBadge, stopReasonLabel } from "./StatusBadges";
import { StatusStepper } from "./StatusStepper";

type TabKey = "negotiation" | "agreement" | "settlement" | "deliverables" | "audit";

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: "negotiation", label: "Negotiation" },
  { key: "agreement", label: "Agreement" },
  { key: "settlement", label: "Settlement" },
  { key: "deliverables", label: "Deliverables" },
  { key: "audit", label: "Audit" },
];

function initialTab(status: DealStatus): TabKey {
  if (status === "DELIVERABLES_SUBMITTED" || status === "DISPUTED") return "deliverables";
  if (status === "AGREED" || status === "SETTLEMENT_PENDING" || status === "IN_PROGRESS" || status === "ACCEPTED" || status === "CLOSED") {
    return "settlement";
  }
  if (status === "TERMS_PENDING_ACCEPTANCE") return "agreement";
  return "negotiation";
}

function nextAction(workspace: DealWorkspaceDTO): { title: string; body: string; tab: TabKey } {
  if (workspace.status === "DISPUTED") {
    return {
      title: "Capture is frozen by dispute",
      body: "The dispute state blocks automated capture and acceptance. Review the evidence and the audit trail; resolution is backend-owned.",
      tab: "deliverables",
    };
  }
  if (!workspace.myMandate) {
    return {
      title: "Lock your private mandate",
      body: "Set hard price, delivery, revision, and content boundaries before the first offer.",
      tab: "negotiation",
    };
  }
  if (workspace.status === "ACTIVE_NEGOTIATION") {
    return {
      title: workspace.offers.length ? "Continue the negotiation" : "Submit the first offer",
      body: "Review the shared round history, then propose terms inside your private mandate.",
      tab: "negotiation",
    };
  }
  if (workspace.status === "TERMS_PENDING_ACCEPTANCE") {
    const ownAccepted = workspace.myRole === "BUYER" ? workspace.currentAgreement?.buyerAcceptedAt : workspace.currentAgreement?.sellerAcceptedAt;
    return ownAccepted
      ? {
          title: "Waiting for the other party",
          body: "Your acceptance is recorded against this exact agreement version and hash.",
          tab: "agreement",
        }
      : {
          title: "Review and accept the agreement",
          body: "Confirm the terms and hash before recording your side of dual consent.",
          tab: "agreement",
        };
  }
  if (workspace.status === "AGREED") {
    return workspace.myRole === "BUYER"
      ? {
          title: "Initiate sandbox settlement",
          body: "Create the commercial invoice and authorization order through the backend.",
          tab: "settlement",
        }
      : {
          title: "Waiting for the buyer",
          body: "The buyer initiates the invoice and authorization hold through PayPal Sandbox.",
          tab: "settlement",
        };
  }
  if (workspace.settlement.paymentStatus === "ORDER_CREATED") {
    return {
      title: "Authorize the hold",
      body: "The order exists; the buyer still needs to authorize the sandbox hold.",
      tab: "settlement",
    };
  }
  if (workspace.status === "IN_PROGRESS") {
    return workspace.myRole === "SELLER"
      ? {
          title: "Upload the deliverable",
          body: "Add the completed work with a version and SHA-256 evidence record.",
          tab: "deliverables",
        }
      : {
          title: "Waiting for the deliverable",
          body: "The seller can upload evidence while the authorization hold is active.",
          tab: "deliverables",
        };
  }
  if (workspace.status === "DELIVERABLES_SUBMITTED") {
    return {
      title: workspace.myRole === "BUYER" ? "Review the deliverable" : "Waiting for buyer review",
      body:
        workspace.myRole === "BUYER"
          ? "Accept to release capture, or open a dispute to freeze settlement."
          : "The buyer reviews the evidence and can accept or dispute it.",
      tab: "deliverables",
    };
  }
  return {
    title: "Deal closed",
    body: "The workspace is read-only. The settlement identifiers and audit timeline remain available.",
    tab: "settlement",
  };
}

export function MockDealWorkspace({ dealId }: { dealId: string }) {
  const { db, currentUser, users, getWorkspace, setCurrentUserId, actions } = useMockData();
  const workspace = getWorkspace(dealId);
  const deal = db.deals[dealId];
  const [tab, setTab] = useState<TabKey>(() => initialTab(workspace?.status ?? "DRAFT"));

  if (!deal) {
    return (
      <EmptyState
        title="Deal not found in the demo dataset"
        description={
          <>
            <code className="rounded bg-slate-200 px-1">{dealId}</code> is not one of the seeded scenarios. Start a buyer
            deal to create one.
          </>
        }
        action={
          <Link
            href="/intake"
            className="rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-800"
          >
            Start a demo deal
          </Link>
        }
      />
    );
  }

  if (!workspace) {
    const participantIds = [deal.buyerUserId, deal.sellerUserId].filter(Boolean) as string[];
    const members = users.filter((user) => participantIds.includes(user.id));
    return (
      <EmptyState
        title="This workspace is scoped to deal participants"
        description={`You are viewing as ${currentUser.email}, which is not a buyer or seller on this deal. The backend would return 403 for any cross-account access attempt.`}
        action={
          <div className="flex flex-wrap gap-2">
            {members.map((member) => (
              <Button key={member.id} variant="secondary" size="sm" onClick={() => setCurrentUserId(member.id)}>
                Switch to {member.displayName}
              </Button>
            ))}
            <Link href="/" className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold">
              Back to dashboard
            </Link>
          </div>
        }
      />
    );
  }

  const action = nextAction(workspace);
  const title = workspace.currentAgreement?.canonicalTerms.title ?? workspace.offers[workspace.offers.length - 1]?.terms.title ?? deal.terms.title;
  const counterpartyEmail = workspace.myRole === "BUYER" ? workspace.sellerEmail : workspace.buyerEmail;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <Link href="/" className="text-xs font-semibold text-slate-500 underline underline-offset-2 hover:text-slate-900">
              ← Dashboard
            </Link>
            <Badge tone="accent">{workspace.myRole === "BUYER" ? "Buyer view" : "Seller view"}</Badge>
            <DealStatusBadge status={workspace.status} />
            {workspace.disputeStatus !== "NONE" ? <DisputeStatusBadge status={workspace.disputeStatus} /> : null}
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">{title || "Untitled deal"}</h1>
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
            <span className="font-mono">{workspace.dealId}</span>
            <span>·</span>
            <span>Counterparty: {counterpartyEmail ?? "not joined"}</span>
            <span>·</span>
            <span>{stopReasonLabel(workspace.stopReason)}</span>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <CopyButton value={workspace.dealId} label="Copy deal ID" />
        </div>
      </div>

      <Card>
        <CardBody className="py-3">
          <StatusStepper status={workspace.status} />
        </CardBody>
      </Card>

      {workspace.status === "DISPUTED" ? (
        <Alert tone="error" title="Dispute freeze is active">
          Automated capture and acceptance actions are blocked by the server state. This view never offers an override.
        </Alert>
      ) : (
        <Alert
          tone="info"
          title={action.title}
          action={
            <Button size="sm" variant="secondary" onClick={() => setTab(action.tab)}>
              Open {tabs.find((item) => item.key === action.tab)?.label}
            </Button>
          }
        >
          {action.body}
        </Alert>
      )}

      <div className="overflow-x-auto border-b border-slate-200">
        <div role="tablist" aria-label="Deal workspace sections" className="flex min-w-max items-center gap-1">
          {tabs.map((item) => {
            const selected = tab === item.key;
            const count =
              item.key === "negotiation"
                ? workspace.offers.length
                : item.key === "deliverables"
                  ? workspace.evidenceList.length
                  : null;
            return (
              <button
                key={item.key}
                type="button"
                role="tab"
                id={`tab-${item.key}`}
                aria-selected={selected}
                aria-controls={`panel-${item.key}`}
                onClick={() => setTab(item.key)}
                className={cn(
                  "flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700",
                  selected
                    ? "border-emerald-700 text-emerald-800"
                    : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-800",
                )}
              >
                {item.label}
                {count !== null ? <Badge tone={selected ? "success" : "neutral"}>{count}</Badge> : null}
              </button>
            );
          })}
        </div>
      </div>

      <div
        key={currentUser.id}
        role="tabpanel"
        id={`panel-${tab}`}
        aria-labelledby={`tab-${tab}`}
        className="pt-1"
      >
        {tab === "negotiation" ? (
          <NegotiationPanel workspace={workspace} onOpenAgreement={() => setTab("agreement")} />
        ) : null}
        {tab === "agreement" ? (
          <AgreementPanel workspace={workspace} onOpenSettlement={() => setTab("settlement")} />
        ) : null}
        {tab === "settlement" ? (
          <SettlementPanel workspace={workspace} onOpenDeliverables={() => setTab("deliverables")} />
        ) : null}
        {tab === "deliverables" ? (
          <DeliverablesPanel workspace={workspace} actions={actions} mode="mock" />
        ) : null}
        {tab === "audit" ? <AuditPanel dealId={workspace.dealId} /> : null}
      </div>

      <p className="text-xs leading-relaxed text-slate-500">
        You are viewing the {workspace.myRole.toLowerCase()} side of the deal. Only your mandate is present in this
        workspace payload; private counterparty boundaries are never rendered.
      </p>
    </div>
  );
}
