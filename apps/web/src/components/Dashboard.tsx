"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { LIVE_CAPABILITIES } from "@/lib/api/capabilities";
import { formatDateTime } from "@/lib/format";
import { useMockData } from "@/providers/MockDataProvider";
import { useSession } from "@/providers/SessionProvider";
import type { MockDealSummary } from "@/lib/mock/types";
import { DealStatusBadge, InvoiceStatusBadge, PaymentStatusBadge } from "@/components/deal/StatusBadges";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";

function scenarioHint(summary: MockDealSummary): string {
  if (summary.status === "DISPUTED") return "Capture frozen by dispute";
  if (summary.status === "DELIVERABLES_SUBMITTED") return "Deliverable awaiting buyer review";
  if (summary.status === "IN_PROGRESS") return "Authorization hold active";
  if (summary.status === "TERMS_PENDING_ACCEPTANCE") return "Agreement awaiting dual consent";
  if (summary.status === "AGREED") return "Ready for sandbox settlement";
  return "Negotiation in progress";
}

function DealCard({ summary }: { summary: MockDealSummary }) {
  return (
    <Card
      className={
        summary.status === "DISPUTED"
          ? "border-rose-200 shadow-sm"
          : summary.status === "TERMS_PENDING_ACCEPTANCE" || summary.status === "DELIVERABLES_SUBMITTED"
            ? "border-amber-200 shadow-sm"
            : undefined
      }
    >
      <CardBody className="flex h-full flex-col gap-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
              {summary.myRole === "BUYER" ? "Buyer workspace" : "Seller workspace"}
            </p>
            <h3 className="mt-0.5 truncate text-base font-semibold text-slate-900">{summary.title}</h3>
            <p className="mt-1 font-mono text-[11px] text-slate-500">{summary.dealId}</p>
          </div>
          <DealStatusBadge status={summary.status} />
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <InvoiceStatusBadge status={summary.settlement.invoiceStatus} />
          <PaymentStatusBadge status={summary.settlement.paymentStatus} />
          <Badge tone="neutral">{summary.offerCount} offers</Badge>
        </div>

        <p className="text-sm leading-relaxed text-slate-600">{scenarioHint(summary)}</p>

        <dl className="grid grid-cols-2 gap-2 text-xs text-slate-500">
          <div>
            <dt className="font-medium text-slate-500">Counterparty</dt>
            <dd className="truncate text-slate-800">{summary.counterpartyEmail ?? "Not joined yet"}</dd>
          </div>
          <div>
            <dt className="font-medium text-slate-500">Updated</dt>
            <dd className="text-slate-800">{formatDateTime(summary.updatedAt)}</dd>
          </div>
        </dl>

        <div className="mt-auto flex items-center justify-between gap-3 pt-1">
          <Link
            href={`/deals/${summary.dealId}`}
            className="inline-flex min-h-9 items-center rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
          >
            Open workspace
          </Link>
          {summary.disputeStatus !== "NONE" ? (
            <span className="text-[11px] font-semibold text-rose-700">Dispute {summary.disputeStatus.toLowerCase()}</span>
          ) : null}
        </div>
      </CardBody>
    </Card>
  );
}

function MockDashboard() {
  const { currentUser, summaries, actions, users, setCurrentUserId } = useMockData();
  const [resetting, setResetting] = useState(false);

  const stats = useMemo(
    () => ({
      active: summaries.filter((item) => ["ACTIVE_NEGOTIATION", "TERMS_PENDING_ACCEPTANCE"].includes(item.status)).length,
      settlement: summaries.filter((item) => ["AGREED", "SETTLEMENT_PENDING", "IN_PROGRESS"].includes(item.status)).length,
      review: summaries.filter((item) => item.status === "DELIVERABLES_SUBMITTED").length,
      disputed: summaries.filter((item) => item.status === "DISPUTED").length,
    }),
    [summaries],
  );

  return (
    <>
      <PageHeader
        eyebrow="Demo workspace"
        title={`Good to see you, ${currentUser.displayName}`}
        description={
          <>
            Four synthetic scenarios cover negotiation, dual consent, sandbox settlement, deliverable review, and a
            capture-freezing dispute. Use <strong>Viewing as</strong> in the header to compare the buyer and seller views.
          </>
        }
        actions={
          <>
            <Link
              href="/intake"
              className="inline-flex min-h-10 items-center rounded-lg bg-indigo-700 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Start a buyer deal
            </Link>
            <Link
              href="/join"
              className="inline-flex min-h-10 items-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 shadow-sm hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500"
            >
              Join with invite
            </Link>
          </>
        }
      />

      <div className="mb-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: "Negotiating", value: stats.active, tone: "text-sky-800" },
          { label: "Settlement stage", value: stats.settlement, tone: "text-emerald-800" },
          { label: "Awaiting review", value: stats.review, tone: "text-amber-800" },
          { label: "Disputed", value: stats.disputed, tone: "text-rose-800" },
        ].map((stat) => (
          <div key={stat.label} className="rounded-xl border border-slate-200 bg-white px-4 py-3 shadow-sm">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{stat.label}</p>
            <p className={`mt-1 text-2xl font-bold ${stat.tone}`}>{stat.value}</p>
          </div>
        ))}
      </div>

      <div className="mb-6 grid gap-3 lg:grid-cols-3">
        <Alert tone="info" title="Privacy by role">
          Only <code className="rounded bg-white/70 px-1">myMandate</code> is rendered. The counterparty limits, targets,
          and forbidden terms never appear in the shared workspace view.
        </Alert>
        <Alert tone="info" title="Money stays integral">
          Prices are stored and sent as integer minor units. Formatting happens at display time without floating-point
          arithmetic.
        </Alert>
        <Alert tone="info" title="Explicitly simulated">
          Demo actions, hashes, and IDs are labelled. In live mode the UI only shows values returned by the backend.
        </Alert>
      </div>

      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-bold uppercase tracking-wide text-slate-600">Deals visible to this user</h2>
        <button
          type="button"
          disabled={resetting}
          onClick={() => {
            setResetting(true);
            actions.resetDemo();
            window.setTimeout(() => setResetting(false), 200);
          }}
          className="text-xs font-semibold text-slate-500 underline underline-offset-2 hover:text-slate-800 disabled:opacity-50"
        >
          Reset demo data
        </button>
      </div>

      {summaries.length === 0 ? (
        <EmptyState
          title="No deals for this demo user"
          description={
            <>
              Switch to the other demo identity or start a new buyer deal. The seller can join with the invite token shown
              after intake.
            </>
          }
          action={
            <div className="flex flex-wrap gap-2">
              {users
                .filter((user) => user.id !== currentUser.id)
                .map((user) => (
                  <Button key={user.id} variant="secondary" size="sm" onClick={() => setCurrentUserId(user.id)}>
                    View as {user.displayName}
                  </Button>
                ))}
            </div>
          }
        />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {summaries.map((summary) => (
            <DealCard key={summary.dealId} summary={summary} />
          ))}
        </div>
      )}
    </>
  );
}

function LiveDashboard() {
  const { liveSession, apiBaseUrl } = useSession();
  const router = useRouter();
  const [dealId, setDealId] = useState("");
  const available = LIVE_CAPABILITIES.filter((item) => item.state === "available");
  const pending = LIVE_CAPABILITIES.filter((item) => item.state === "pending");

  return (
    <>
      <PageHeader
        eyebrow="Live API mode"
        title="Server-confirmed workspace"
        description={
          <>
            This build calls only <code className="rounded bg-slate-200 px-1">{apiBaseUrl}</code>. The browser never calls
            PayPal and never invents payment, agreement, evidence, or audit values.
          </>
        }
        actions={
          liveSession ? (
            <Link
              href="/intake"
              className="inline-flex min-h-10 items-center rounded-lg bg-indigo-700 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Start live intake
            </Link>
          ) : (
            <Link
              href="/login"
              className="inline-flex min-h-10 items-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Sign in
            </Link>
          )
        }
      />

      {!liveSession ? (
        <Alert tone="warning" title="Sign in required">
          Live mode uses a JWT from the Payagent API. Sign in with your local backend account; the token is kept in session
          storage for this tab.
        </Alert>
      ) : (
        <Alert tone="success" title={`Signed in as ${liveSession.email}`}>
          The live console loads only endpoints that exist today. Missing backend routes are listed below and stay disabled.
        </Alert>
      )}

      <div className="mt-5 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader
            title="Open a live deal console"
            description="Paste a deal UUID returned by intake, or open the console from a successful intake response."
          />
          <CardBody>
            <form
              className="flex flex-col gap-2 sm:flex-row"
              onSubmit={(event) => {
                event.preventDefault();
                const trimmed = dealId.trim();
                if (trimmed) router.push(`/deals/${encodeURIComponent(trimmed)}`);
              }}
            >
              <label htmlFor="live-deal-id" className="sr-only">
                Deal ID
              </label>
              <input
                id="live-deal-id"
                value={dealId}
                onChange={(event) => setDealId(event.target.value)}
                placeholder="deal UUID"
                className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-emerald-600 focus:outline-none focus:ring-2 focus:ring-emerald-600/20"
              />
              <Button type="submit" disabled={!dealId.trim() || !liveSession}>
                Open console
              </Button>
            </form>
            <p className="mt-3 text-xs leading-relaxed text-slate-500">
              The full workspace snapshot endpoint is pending. Until Member A adds it, status-based actions are available
              only when this browser has a server response confirming the current step.
            </p>
          </CardBody>
        </Card>

        <Card>
          <CardHeader title="Available against the current API" description="Verified from the controllers in this repo." />
          <CardBody>
            <ul className="space-y-2 text-sm text-slate-700">
              {available.map((capability) => (
                <li key={capability.id} className="flex gap-2">
                  <span aria-hidden="true" className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full bg-emerald-600" />
                  <span>
                    <span className="font-semibold">{capability.label}</span>
                    <span className="block font-mono text-[11px] text-slate-500">{capability.endpoint}</span>
                  </span>
                </li>
              ))}
            </ul>
          </CardBody>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHeader
          title="Pending backend routes"
          description="These actions stay honestly unavailable in live mode until their owners publish the route."
        />
        <CardBody>
          <div className="grid gap-3 md:grid-cols-2">
            {pending.map((capability) => (
              <div key={capability.id} className="rounded-lg border border-amber-200 bg-amber-50/60 px-3 py-2.5">
                <div className="flex flex-wrap items-center justify-between gap-1">
                  <p className="text-sm font-semibold text-amber-950">{capability.label}</p>
                  <Badge tone="warning">{capability.owner}</Badge>
                </div>
                <p className="mt-1 font-mono text-[11px] text-amber-900/80">{capability.endpoint}</p>
                <p className="mt-1 text-xs leading-relaxed text-amber-900/80">{capability.note}</p>
              </div>
            ))}
          </div>
        </CardBody>
      </Card>
    </>
  );
}

export function Dashboard() {
  const { mode } = useSession();
  return mode === "mock" ? <MockDashboard /> : <LiveDashboard />;
}
