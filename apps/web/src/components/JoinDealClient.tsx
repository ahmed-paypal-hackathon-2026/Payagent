"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { writeLiveDealSession } from "@/lib/live-session";
import { useMockData } from "@/providers/MockDataProvider";
import { useSession } from "@/providers/SessionProvider";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Field, Input } from "@/components/ui/Field";
import { PageHeader } from "@/components/ui/PageHeader";

export function JoinDealClient() {
  const { mode, liveSession, apiBaseUrl } = useSession();
  const { actions, currentUser } = useMockData();
  const [dealId, setDealId] = useState("");
  const [inviteToken, setInviteToken] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [joinedDealId, setJoinedDealId] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    if (!dealId.trim() || !inviteToken.trim()) {
      setError("Both the deal ID and the private invite token are required.");
      return;
    }

    setSubmitting(true);
    try {
      if (mode === "mock") {
        await actions.joinDeal(dealId.trim(), inviteToken.trim());
        setJoinedDealId(dealId.trim());
        return;
      }

      if (!liveSession) {
        setError("Sign in before joining a live deal.");
        return;
      }
      await api.joinDeal(dealId.trim(), inviteToken.trim(), liveSession.token);
      writeLiveDealSession(dealId.trim(), {
        roleHint: "SELLER",
        inviteToken: inviteToken.trim(),
        statusHint: "ACTIVE_NEGOTIATION",
      });
      setJoinedDealId(dealId.trim());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The deal could not be joined.");
    } finally {
      setSubmitting(false);
    }
  }

  if (mode === "live" && !liveSession) {
    return (
      <>
        <PageHeader
          eyebrow="Seller invite"
          title="Join a live deal"
          description="The invite token is sent in the request body to the Payagent API, never in the URL."
        />
        <Alert
          tone="warning"
          title="Sign in required"
          action={
            <Link href="/login" className="font-semibold underline underline-offset-2">
              Sign in
            </Link>
          }
        >
          Use the seller account that should join the deal. Requests go only to {apiBaseUrl}.
        </Alert>
      </>
    );
  }

  if (joinedDealId) {
    return (
      <>
        <PageHeader
          eyebrow="Seller workspace"
          title={mode === "mock" ? "You joined the demo deal" : "The API accepted your join request"}
          description={
            mode === "mock"
              ? `You are now viewing this deal as ${currentUser.displayName}. Lock your seller mandate before submitting an offer.`
              : "Join succeeded only because the backend returned a success response. Lock your seller mandate next."
          }
        />
        <Alert
          tone="success"
          title="Deal joined"
          action={
            <Link
              href={`/deals/${encodeURIComponent(joinedDealId)}`}
              className="whitespace-nowrap rounded-lg bg-emerald-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-800"
            >
              Open workspace
            </Link>
          }
        >
          Deal <code className="rounded bg-white/70 px-1">{joinedDealId}</code> is available to this account.
        </Alert>
      </>
    );
  }

  return (
    <>
      <PageHeader
        eyebrow="Seller invite"
        title="Join with a private invite"
        description="The buyer shares a deal ID and invite token privately. The token is not appended to the workspace URL."
      />

      {mode === "mock" ? (
        <Alert tone="info" className="mb-5" title="How to try this in demo mode">
          Start a buyer deal from <Link href="/intake" className="font-semibold underline underline-offset-2">New deal</Link>,
          copy its invite token, switch the header to <strong>Demo Seller</strong>, then join with that deal ID and token.
          The four preloaded scenarios already have both parties.
        </Alert>
      ) : (
        <Alert tone="info" className="mb-5" title="Live request">
          This calls <code className="rounded bg-white/70 px-1">POST /api/v1/deals/{"{id}"}/join</code> on {apiBaseUrl} with an
          idempotency key. A success panel appears only after the backend accepts the request.
        </Alert>
      )}

      <Card className="max-w-2xl border-sky-100 bg-white/95 shadow-sm backdrop-blur">
        <div className="h-1.5 w-full bg-gradient-to-r from-[#003087] via-[#0070ba] to-[#0079c1]" />
        <CardHeader
          title={
            <div className="flex items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-sky-100 text-xs font-bold text-[#003087]">
                🤝
              </span>
              <span>Private Deal Credentials</span>
            </div>
          }
          description="Enter the Deal ID and private token received from the buyer."
        />
        <CardBody className="p-6">
          <form onSubmit={handleSubmit} className="space-y-5" noValidate>
            {error ? <Alert tone="error">{error}</Alert> : null}
            <Field label="Deal Identifier" htmlFor="join-deal-id" hint="Standard UUID shared by the buyer." required>
              <Input
                id="join-deal-id"
                value={dealId}
                onChange={(event) => setDealId(event.target.value)}
                placeholder="e.g. 5b9c2a11-..."
                autoComplete="off"
                className="font-mono"
              />
            </Field>
            <Field
              label="Confidential Invite Token"
              htmlFor="join-invite-token"
              hint="Kept strictly confidential out of URL parameters, logs, and external payloads."
              required
            >
              <Input
                id="join-invite-token"
                value={inviteToken}
                onChange={(event) => setInviteToken(event.target.value)}
                placeholder="Paste the secret invite token"
                autoComplete="off"
                spellCheck={false}
                className="font-mono"
              />
            </Field>
            <div className="flex flex-wrap items-center gap-3 pt-2">
              <Button type="submit" loading={submitting}>
                Connect to Deal Room →
              </Button>
              <Link href="/" className="text-xs font-bold text-slate-500 hover:text-slate-900 transition">
                Return to Dashboard
              </Link>
            </div>
          </form>
        </CardBody>
      </Card>
    </>
  );
}
