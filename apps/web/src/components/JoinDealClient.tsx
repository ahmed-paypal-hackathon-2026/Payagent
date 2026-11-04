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

      <Card className="max-w-2xl">
        <CardHeader title="Invite details" description="Ask the buyer for both values through a private channel." />
        <CardBody>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {error ? <Alert tone="error">{error}</Alert> : null}
            <Field label="Deal ID" htmlFor="join-deal-id" required>
              <Input
                id="join-deal-id"
                value={dealId}
                onChange={(event) => setDealId(event.target.value)}
                placeholder="deal UUID from the buyer"
                autoComplete="off"
              />
            </Field>
            <Field
              label="Invite token"
              htmlFor="join-invite-token"
              hint="Kept out of the URL, logs, and workspace payloads."
              required
            >
              <Input
                id="join-invite-token"
                value={inviteToken}
                onChange={(event) => setInviteToken(event.target.value)}
                placeholder="Private invite token"
                autoComplete="off"
                spellCheck={false}
              />
            </Field>
            <div className="flex flex-wrap items-center gap-3">
              <Button type="submit" loading={submitting}>
                Join deal
              </Button>
              <Link href="/" className="text-xs font-semibold text-slate-600 underline underline-offset-2 hover:text-slate-900">
                Back to dashboard
              </Link>
            </div>
          </form>
        </CardBody>
      </Card>
    </>
  );
}
