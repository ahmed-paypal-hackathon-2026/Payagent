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
  const isBuyer = summary.myRole === "BUYER";

  return (
    <div
      className={`group relative overflow-hidden rounded-2xl border transition-all duration-200 hover:-translate-y-1 hover:shadow-lg ${
        summary.status === "DISPUTED"
          ? "border-rose-200 bg-white ring-1 ring-rose-100/80 hover:border-rose-300"
          : summary.status === "TERMS_PENDING_ACCEPTANCE" || summary.status === "DELIVERABLES_SUBMITTED"
            ? "border-amber-200 bg-white ring-1 ring-amber-100/80 hover:border-amber-300"
            : "border-slate-200/90 bg-white hover:border-slate-300 shadow-2xs"
      }`}
    >
      {/* Top Accent Gradient Bar */}
      <div
        className={`h-1.5 w-full ${
          summary.status === "DISPUTED"
            ? "bg-gradient-to-r from-rose-500 to-amber-500"
            : summary.status === "AGREED" || summary.status === "ACCEPTED" || summary.status === "CLOSED"
              ? "bg-gradient-to-r from-emerald-500 to-teal-400"
              : isBuyer
                ? "bg-gradient-to-r from-sky-500 to-indigo-500"
                : "bg-gradient-to-r from-emerald-500 to-cyan-500"
        }`}
      />

      <div className="flex h-full flex-col p-5">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div className="min-w-0">
            <span
              className={`inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                isBuyer ? "bg-sky-50 text-sky-700 border border-sky-100" : "bg-emerald-50 text-emerald-700 border border-emerald-100"
              }`}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${isBuyer ? "bg-sky-500" : "bg-emerald-500"}`} />
              {isBuyer ? "Buyer Workspace" : "Seller Workspace"}
            </span>
            <h3 className="mt-2 truncate text-base font-bold text-slate-900 group-hover:text-emerald-700 transition">
              {summary.title}
            </h3>
            <p className="mt-0.5 font-mono text-[10px] text-slate-400 select-all">{summary.dealId}</p>
          </div>
          <DealStatusBadge status={summary.status} />
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-1.5">
          <InvoiceStatusBadge status={summary.settlement.invoiceStatus} />
          <PaymentStatusBadge status={summary.settlement.paymentStatus} />
          <Badge tone="neutral">{summary.offerCount} rounds</Badge>
        </div>

        <div className="mt-3 rounded-xl bg-slate-50/90 p-3 border border-slate-100 text-xs text-slate-600">
          <div className="flex items-center gap-1.5 font-semibold text-slate-700">
            <span className="h-1.5 w-1.5 rounded-full bg-slate-400" />
            <span>Milestone Target</span>
          </div>
          <p className="mt-1 text-slate-600 leading-relaxed">{scenarioHint(summary)}</p>
        </div>

        <dl className="mt-3 grid grid-cols-2 gap-2 text-xs">
          <div className="rounded-xl bg-slate-50/50 p-2.5 border border-slate-100/60">
            <dt className="text-[10px] uppercase font-bold text-slate-400">Counterparty</dt>
            <dd className="mt-0.5 truncate font-semibold text-slate-800">{summary.counterpartyEmail ?? "Awaiting invite"}</dd>
          </div>
          <div className="rounded-xl bg-slate-50/50 p-2.5 border border-slate-100/60">
            <dt className="text-[10px] uppercase font-bold text-slate-400">Activity</dt>
            <dd className="mt-0.5 font-semibold text-slate-800">{formatDateTime(summary.updatedAt)}</dd>
          </div>
        </dl>

        <div className="mt-5 flex items-center justify-between gap-3 pt-3 border-t border-slate-100">
          <Link
            href={`/deals/${summary.dealId}`}
            className="inline-flex min-h-9 items-center gap-2 rounded-xl bg-gradient-to-r from-[#003087] to-[#0070ba] px-4 py-2 text-xs font-bold text-white shadow-sm shadow-blue-900/10 hover:shadow-md hover:shadow-blue-500/25 active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-600"
          >
            <span>Enter Deal Room</span>
            <svg className="h-3.5 w-3.5 transition-transform group-hover:translate-x-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
            </svg>
          </Link>
          {summary.disputeStatus !== "NONE" ? (
            <span className="inline-flex items-center gap-1.5 rounded-lg bg-rose-50 px-2.5 py-1 text-[11px] font-bold text-rose-700 border border-rose-100">
              <span className="h-1.5 w-1.5 rounded-full bg-rose-500 animate-ping" />
              Dispute {summary.disputeStatus.toLowerCase()}
            </span>
          ) : null}
        </div>
      </div>
    </div>
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
        eyebrow="PayPal Sandbox Escrow Environment"
        title={`Welcome back, ${currentUser.displayName}`}
        description={
          <>
            Autonomous bilateral deal room with automated PayPal sandbox settlement and AI-bounded negotiation. Use <strong>Perspective</strong> in the top bar to test actions as Buyer or Seller.
          </>
        }
        actions={
          <>
            <Link
              href="/intake"
              className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-gradient-to-r from-[#003087] via-[#0070ba] to-[#0079c1] px-4.5 py-2 text-xs font-bold text-white shadow-md shadow-sky-900/15 hover:shadow-lg hover:shadow-sky-500/25 active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-600"
            >
              <svg className="h-4 w-4 text-cyan-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.2} d="M12 4v16m8-8H4" />
              </svg>
              <span>Create buyer deal</span>
            </Link>
            <Link
              href="/join"
              className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-slate-200/90 bg-white/90 px-4.5 py-2 text-xs font-bold text-slate-800 shadow-2xs hover:bg-slate-50 hover:border-slate-300 active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500"
            >
              <span>Join with invite</span>
            </Link>
          </>
        }
      />

      <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {[
          {
            label: "Negotiating",
            value: stats.active,
            gradient: "from-sky-500 to-blue-600",
            bg: "bg-sky-50/70 border-sky-100",
            textColor: "text-[#0070ba]",
            hint: "Active offer rounds",
            icon: "💬",
          },
          {
            label: "Settlement stage",
            value: stats.settlement,
            gradient: "from-blue-600 to-indigo-600",
            bg: "bg-blue-50/70 border-blue-100",
            textColor: "text-[#003087]",
            hint: "Invoice & hold active",
            icon: "💳",
          },
          {
            label: "Awaiting review",
            value: stats.review,
            gradient: "from-amber-500 to-orange-500",
            bg: "bg-amber-50/70 border-amber-100",
            textColor: "text-amber-700",
            hint: "Deliverables submitted",
            icon: "📦",
          },
          {
            label: "Disputed / Frozen",
            value: stats.disputed,
            gradient: "from-rose-500 to-red-600",
            bg: "bg-rose-50/70 border-rose-100",
            textColor: "text-rose-700",
            hint: "Funds held in escrow",
            icon: "🛡️",
          },
        ].map((stat) => (
          <div
            key={stat.label}
            className="group relative overflow-hidden rounded-2xl border border-slate-200/80 bg-white/95 p-5 shadow-2xs backdrop-blur transition-all duration-300 hover:-translate-y-1 hover:shadow-md"
          >
            <div className={`absolute top-0 left-0 right-0 h-1 bg-gradient-to-r ${stat.gradient}`} />
            <div className="flex items-center justify-between">
              <span className="text-lg">{stat.icon}</span>
              <span className={`rounded-lg px-2 py-0.5 text-[10px] font-bold ${stat.bg} ${stat.textColor}`}>
                {stat.hint}
              </span>
            </div>
            <p className="mt-2 text-xs font-bold uppercase tracking-wider text-slate-400">{stat.label}</p>
            <p className="mt-1 text-3xl font-black tracking-tight text-slate-900 group-hover:scale-105 transition-transform origin-left">
              {stat.value}
            </p>
          </div>
        ))}
      </div>

      <div className="mb-8 grid gap-3 sm:grid-cols-3">
        <div className="group relative overflow-hidden rounded-2xl border border-sky-100 bg-gradient-to-br from-white via-white to-sky-50/60 p-5 shadow-2xs transition-all hover:shadow-md hover:border-sky-200">
          <div className="flex items-center gap-2.5 text-xs font-bold text-slate-800">
            <span className="flex h-7 w-7 items-center justify-center rounded-xl bg-sky-100 text-sky-700 shadow-2xs">🔒</span>
            Zero-Knowledge Mandates
          </div>
          <p className="mt-2 text-xs text-slate-500 leading-relaxed">
            Private limits, target pricing, and utility curves stay strictly confidential. Counterparties never see confidential thresholds.
          </p>
        </div>
        <div className="group relative overflow-hidden rounded-2xl border border-blue-100 bg-gradient-to-br from-white via-white to-blue-50/60 p-5 shadow-2xs transition-all hover:shadow-md hover:border-blue-200">
          <div className="flex items-center gap-2.5 text-xs font-bold text-slate-800">
            <span className="flex h-7 w-7 items-center justify-center rounded-xl bg-blue-100 text-[#003087] shadow-2xs">💳</span>
            Integer Minor Units
          </div>
          <p className="mt-2 text-xs text-slate-500 leading-relaxed">
            All PayPal escrow holds, invoices, and payouts are tracked strictly as integer cents, eliminating floating-point rounding drift.
          </p>
        </div>
        <div className="group relative overflow-hidden rounded-2xl border border-amber-100 bg-gradient-to-br from-white via-white to-amber-50/60 p-5 shadow-2xs transition-all hover:shadow-md hover:border-amber-200">
          <div className="flex items-center gap-2.5 text-xs font-bold text-slate-800">
            <span className="flex h-7 w-7 items-center justify-center rounded-xl bg-amber-100 text-amber-800 shadow-2xs">⚡</span>
            Dispute Fund Freeze
          </div>
          <p className="mt-2 text-xs text-slate-500 leading-relaxed">
            Raising a dispute instantly freezes automated capture across both API calls and asynchronous webhooks until resolution.
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
