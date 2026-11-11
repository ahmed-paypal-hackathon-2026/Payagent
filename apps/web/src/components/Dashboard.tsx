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
          ? "border-rose-200/90 shadow-sm ring-1 ring-rose-100"
          : summary.status === "TERMS_PENDING_ACCEPTANCE" || summary.status === "DELIVERABLES_SUBMITTED"
            ? "border-amber-200/90 shadow-sm ring-1 ring-amber-100"
            : undefined
      }
    >
      <CardBody className="flex h-full flex-col gap-4">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <span className="inline-flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <span className={`h-1.5 w-1.5 rounded-full ${summary.myRole === "BUYER" ? "bg-sky-500" : "bg-emerald-500"}`} />
              {summary.myRole === "BUYER" ? "Buyer perspective" : "Seller perspective"}
            </span>
            <h3 className="mt-1 truncate text-base font-bold text-slate-900 group-hover:text-emerald-700 transition">
              {summary.title}
            </h3>
            <p className="mt-1 font-mono text-[10px] text-slate-400 select-all">{summary.dealId}</p>
          </div>
          <DealStatusBadge status={summary.status} />
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <InvoiceStatusBadge status={summary.settlement.invoiceStatus} />
          <PaymentStatusBadge status={summary.settlement.paymentStatus} />
          <Badge tone="neutral">{summary.offerCount} rounds</Badge>
        </div>

        <p className="text-xs leading-relaxed text-slate-600 bg-slate-50/80 rounded-xl p-2.5 border border-slate-100">
          <span className="font-semibold text-slate-700">Next milestone:</span> {scenarioHint(summary)}
        </p>

        <dl className="grid grid-cols-2 gap-2 text-xs text-slate-500">
          <div className="rounded-lg bg-slate-50/50 p-2">
            <dt className="text-[10px] uppercase font-semibold text-slate-400">Counterparty</dt>
            <dd className="mt-0.5 truncate font-medium text-slate-800">{summary.counterpartyEmail ?? "Awaiting invite"}</dd>
          </div>
          <div className="rounded-lg bg-slate-50/50 p-2">
            <dt className="text-[10px] uppercase font-semibold text-slate-400">Last activity</dt>
            <dd className="mt-0.5 font-medium text-slate-800">{formatDateTime(summary.updatedAt)}</dd>
          </div>
        </dl>

        <div className="mt-auto flex items-center justify-between gap-3 pt-2 border-t border-slate-100">
          <Link
            href={`/deals/${summary.dealId}`}
            className="inline-flex min-h-8.5 items-center gap-1.5 rounded-xl bg-slate-900 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-slate-800 active:scale-95 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            <span>Open workspace</span>
            <svg className="h-3.5 w-3.5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </Link>
          {summary.disputeStatus !== "NONE" ? (
            <span className="inline-flex items-center gap-1 text-[11px] font-bold text-rose-700">
              <span className="h-1.5 w-1.5 rounded-full bg-rose-500 animate-pulse" />
              Dispute {summary.disputeStatus.toLowerCase()}
            </span>
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
        eyebrow="Demo environment"
        title={`Welcome back, ${currentUser.displayName}`}
        description={
          <>
            Autonomous bilateral deal room with automated PayPal sandbox settlement and AI-bounded negotiation. Use <strong>Viewing as</strong> in the top bar to switch perspectives.
          </>
        }
        actions={
          <>
            <Link
              href="/intake"
              className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-slate-800 active:scale-95 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
            >
              <svg className="h-4 w-4 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              <span>Create buyer deal</span>
            </Link>
            <Link
              href="/join"
              className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-semibold text-slate-800 shadow-2xs hover:bg-slate-50 hover:border-slate-300 active:scale-95 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500"
            >
              <span>Join with invite</span>
            </Link>
          </>
        }
      />

      <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          { label: "Negotiating", value: stats.active, tone: "text-sky-700 bg-sky-50/70 border-sky-100", hint: "Active offer rounds" },
          { label: "Settlement stage", value: stats.settlement, tone: "text-emerald-700 bg-emerald-50/70 border-emerald-100", hint: "Agreed / In progress" },
          { label: "Awaiting review", value: stats.review, tone: "text-amber-700 bg-amber-50/70 border-amber-100", hint: "Deliverables submitted" },
          { label: "Disputed / Frozen", value: stats.disputed, tone: "text-rose-700 bg-rose-50/70 border-rose-100", hint: "Funds safely locked" },
        ].map((stat) => (
          <div key={stat.label} className="relative overflow-hidden rounded-2xl border border-slate-200/80 bg-white p-5 shadow-2xs transition-all hover:shadow-md">
            <div className="flex items-center justify-between">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">{stat.label}</p>
              <span className={`rounded-lg px-2 py-0.5 text-[10px] font-bold ${stat.tone}`}>
                {stat.hint}
              </span>
            </div>
            <p className="mt-3 text-3xl font-extrabold tracking-tight text-slate-900">{stat.value}</p>
          </div>
        ))}
      </div>

      <div className="mb-8 grid gap-3 sm:grid-cols-3">
        <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs">
          <div className="flex items-center gap-2 text-xs font-bold text-slate-800">
            <span className="flex h-5 w-5 items-center justify-center rounded-md bg-emerald-100 text-emerald-800">🔒</span>
            Zero-Knowledge Mandates
          </div>
          <p className="mt-1 text-xs text-slate-500 leading-relaxed">
            Targets, reservation prices, and utility scores stay private on the client. Counterparties never see confidential thresholds.
          </p>
        </div>
        <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs">
          <div className="flex items-center gap-2 text-xs font-bold text-slate-800">
            <span className="flex h-5 w-5 items-center justify-center rounded-md bg-sky-100 text-sky-800">⚡</span>
            Integer Minor Units
          </div>
          <p className="mt-1 text-xs text-slate-500 leading-relaxed">
            All escrow, invoices, and payouts are tracked strictly as integer cents, eliminating floating-point rounding drifts.
          </p>
        </div>
        <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs">
          <div className="flex items-center gap-2 text-xs font-bold text-slate-800">
            <span className="flex h-5 w-5 items-center justify-center rounded-md bg-amber-100 text-amber-800">🛡</span>
            Dispute Fund Freeze
          </div>
          <p className="mt-1 text-xs text-slate-500 leading-relaxed">
            Raising a dispute instantly halts automated PayPal capture across both API and webhook workers until resolution.
          </p>
        </div>
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
              className="inline-flex min-h-10 items-center rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-emerald-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
            >
              Start live intake
            </Link>
          ) : (
            <Link
              href="/login"
              className="inline-flex min-h-10 items-center rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
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
