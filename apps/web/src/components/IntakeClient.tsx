"use client";

import Link from "next/link";
import { useState } from "react";
import type { StructuredTermsDTO } from "@/types/contracts";
import { api } from "@/lib/api/client";
import { writeLiveDealSession } from "@/lib/live-session";
import { draftFromTerms, termsFromDraft, type FieldErrors, type TermsDraft } from "@/lib/forms";
import { useMockData } from "@/providers/MockDataProvider";
import { useSession } from "@/providers/SessionProvider";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { CopyButton } from "@/components/ui/CopyButton";
import { Field, Select, Textarea } from "@/components/ui/Field";
import { PageHeader } from "@/components/ui/PageHeader";
import { TermsFields } from "@/components/forms/TermsFields";
import { TermsSummary } from "@/components/deal/TermsSummary";

const DEMO_BRIEF =
  "I need a responsive landing page for the Payagent demo. It should include a brand-aligned Tailwind design system, a contact form with validation, and match an approved wireframe. Budget around $900, delivery in two weeks, two revision rounds. Mobile Lighthouse score at least 85.";

const DEMO_TERMS: StructuredTermsDTO = {
  title: "Landing page redesign for Payagent demo",
  deliverables: [
    "Responsive landing page (Next.js)",
    "Brand-aligned Tailwind design system",
    "Contact form with validation",
  ],
  priceMinorUnits: 90000,
  currency: "USD",
  deliveryDays: 14,
  includedRevisions: 2,
  acceptanceCriteriaText:
    "Page matches approved wireframe; Lighthouse performance score >= 85 on mobile.",
};

interface IntakeResult {
  dealId: string;
  terms: StructuredTermsDTO;
  inviteToken: string | null;
  source: "demo" | "server";
}

export function IntakeClient() {
  const { mode, liveSession, apiBaseUrl } = useSession();
  const { actions } = useMockData();
  const [rawPrompt, setRawPrompt] = useState(DEMO_BRIEF);
  const [currency, setCurrency] = useState("USD");
  const [draft, setDraft] = useState<TermsDraft>(() => draftFromTerms(DEMO_TERMS));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<IntakeResult | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    if (!rawPrompt.trim()) {
      setFormError("Describe the work in the brief before extracting terms.");
      return;
    }

    setSubmitting(true);
    try {
      if (mode === "mock") {
        const parsed = termsFromDraft(draft);
        setErrors(parsed.errors);
        if (!parsed.value) {
          setFormError("Review the structured terms. The demo engine uses them exactly as entered.");
          return;
        }
        const demoResult = await actions.createIntake({
          rawPrompt,
          currency,
          terms: parsed.value,
        });
        setResult({
          dealId: demoResult.dealId,
          terms: demoResult.terms,
          inviteToken: demoResult.inviteToken,
          source: "demo",
        });
        return;
      }

      if (!liveSession) {
        setFormError("Sign in before starting a live intake.");
        return;
      }
      const response = await api.intake(rawPrompt, currency, liveSession.token);
      writeLiveDealSession(response.dealId, {
        roleHint: "BUYER",
        terms: response.terms,
        inviteToken: response.inviteToken,
        statusHint: "ACTIVE_NEGOTIATION",
      });
      setResult({
        dealId: response.dealId,
        terms: response.terms,
        inviteToken: response.inviteToken ?? null,
        source: "server",
      });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Intake could not be created.");
    } finally {
      setSubmitting(false);
    }
  }

  if (mode === "live" && !liveSession) {
    return (
      <>
        <PageHeader
          eyebrow="Buyer intake"
          title="Start a live deal"
          description="Live intake calls the Payagent API and displays only the terms the server extracts."
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
          The API requires a JWT for intake. The browser will send the brief only to {apiBaseUrl}.
        </Alert>
      </>
    );
  }

  if (result) {
    return (
      <>
        <PageHeader
          eyebrow={result.source === "demo" ? "Demo intake complete" : "Server intake complete"}
          title={result.source === "demo" ? "Deal created in the demo workspace" : "The API created your deal"}
          description={
            result.source === "demo"
              ? "A synthetic deal was created locally. Invite the seller with the token below, or switch demo users to see the seller side."
              : "The deal and extracted terms below came from the backend response. No local values were substituted."
          }
        />

        <div className="grid gap-5 lg:grid-cols-5">
          <Card className="lg:col-span-3 border-sky-100 bg-white/95 shadow-sm backdrop-blur">
            <CardHeader
              title={
                <div className="flex items-center gap-2">
                  <span className="flex h-6 w-6 items-center justify-center rounded-lg bg-sky-100 text-xs font-bold text-[#003087]">
                    ✓
                  </span>
                  <span>Extracted Contract Terms</span>
                </div>
              }
              description={result.source === "server" ? "Authoritative server extraction" : "Simulated demo terms"}
            />
            <CardBody>
              <TermsSummary terms={result.terms} />
            </CardBody>
          </Card>

          <div className="space-y-4 lg:col-span-2">
            <Card className="border-sky-100 bg-white/95 shadow-sm backdrop-blur">
              <CardHeader title="Next Steps & Coordination" />
              <CardBody className="space-y-4">
                <div>
                  <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Bilateral Deal ID</p>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <code className="break-all rounded-xl bg-slate-100/80 px-2.5 py-1.5 font-mono text-xs font-bold text-slate-800">
                      {result.dealId}
                    </code>
                    <CopyButton value={result.dealId} label="Copy ID" />
                  </div>
                  <Link
                    href={`/deals/${encodeURIComponent(result.dealId)}`}
                    className="mt-3.5 inline-flex min-h-10 items-center gap-2 rounded-xl bg-gradient-to-r from-[#003087] via-[#0070ba] to-[#0079c1] px-4.5 py-2 text-xs font-bold text-white shadow-md shadow-sky-900/15 hover:shadow-lg hover:shadow-sky-500/25 active:scale-95 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-600"
                  >
                    <span>Open Deal Room</span>
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                    </svg>
                  </Link>
                </div>

                <div className="rounded-xl border border-amber-200/80 bg-amber-50/60 p-3.5">
                  <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-amber-900">
                    <span>🔑</span>
                    <span>Confidential Seller Invite Token</span>
                  </div>
                  {result.inviteToken ? (
                    <>
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <code className="break-all rounded-lg bg-white px-2.5 py-1 font-mono text-xs font-bold text-slate-800 border border-amber-200/60">
                          {result.inviteToken}
                        </code>
                        <CopyButton value={result.inviteToken} label="Copy token" />
                      </div>
                      <p className="mt-2 text-[11px] leading-relaxed text-amber-950/80">
                        Deliver this privately to the seller. For cryptographic privacy, the invite token is never published in the URL or logs.
                      </p>
                    </>
                  ) : (
                    <p className="mt-1 text-xs leading-relaxed text-amber-800">
                      The API response did not include an invite token.
                    </p>
                  )}
                </div>

                <div className="border-t border-slate-100 pt-3">
                  <Button variant="secondary" size="sm" onClick={() => setResult(null)}>
                    Start another deal
                  </Button>
                </div>
              </CardBody>
            </Card>

            <Alert tone={result.source === "demo" ? "info" : "success"} title="What is authoritative?">
              {result.source === "demo"
                ? "Demo terms are local test data. They are not an AI extraction and are not sent to a backend."
                : "These terms were returned by POST /api/v1/deals/intake. Use the workspace console for server-confirmed actions."}
            </Alert>
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader
        eyebrow="Buyer intake"
        title="Turn a brief into bounded terms"
        description={
          mode === "mock"
            ? "In demo mode you fill the structured fields yourself. The local engine labels this as simulated intake; no LLM is called."
            : "In live mode the backend extraction service converts the brief into structured terms. The editable demo fields are not available because the live request sends only the brief and currency."
        }
      />

      {mode === "live" ? (
        <Alert tone="info" className="mb-5">
          The live request goes to <code className="rounded bg-white/70 px-1">POST /api/v1/deals/intake</code> on{" "}
          {apiBaseUrl}. The terms you see next will be the server response.
        </Alert>
      ) : (
        <Alert tone="info" className="mb-5" title="Demo intake is manual">
          This is a UI rehearsal, not an AI extraction. The structured values below are exactly what the demo deal will use.
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Card>
          <CardHeader title="Brief" description="Describe the outcome, scope, budget range, timeline, and acceptance expectations." />
          <CardBody className="space-y-4">
            {formError ? <Alert tone="error">{formError}</Alert> : null}
            <Field label="Project brief" htmlFor="intake-brief" required>
              <Textarea
                id="intake-brief"
                rows={6}
                value={rawPrompt}
                onChange={(event) => setRawPrompt(event.target.value)}
                placeholder="Describe the work you need done..."
              />
            </Field>
            <Field
              label="Currency"
              htmlFor="intake-currency"
              hint={mode === "mock" ? "Also updates the demo structured terms." : "Sent with the intake request."}
              className="max-w-xs"
              required
            >
              <Select
                id="intake-currency"
                value={currency}
                onChange={(event) => {
                  const nextCurrency = event.target.value;
                  setCurrency(nextCurrency);
                  if (mode === "mock") {
                    setDraft((current) => ({ ...current, currency: nextCurrency }));
                  }
                }}
              >
                <option value="USD">USD</option>
                <option value="EUR">EUR</option>
                <option value="GBP">GBP</option>
              </Select>
            </Field>
          </CardBody>
        </Card>

        {mode === "mock" ? (
          <Card>
            <CardHeader
              title="Structured terms (demo)"
              description="Fill these as the local stand-in for the AI extractor. Prices are converted to integer minor units."
            />
            <CardBody>
              <TermsFields
                draft={draft}
                onChange={setDraft}
                errors={errors}
                idPrefix="intake-terms"
                showCurrency={false}
              />
            </CardBody>
          </Card>
        ) : null}

        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" loading={submitting}>
            {mode === "mock" ? "Create demo deal" : "Extract terms and create deal"}
          </Button>
          <p className="text-xs leading-relaxed text-slate-500">
            The buyer mandate is a separate private step in the deal workspace.
          </p>
        </div>
      </form>
    </>
  );
}
