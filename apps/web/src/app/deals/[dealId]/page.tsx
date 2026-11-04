"use client";

import { useEffect, useState } from "react";
import {
  fetchWorkspace,
  acceptAgreement,
  formatMinorUnits,
  diffTerms,
  type TermDiff,
} from "@/lib/api";
import type {
  DealWorkspaceDTO,
  OfferDTO,
} from "@/types/contracts";

type Role = "BUYER" | "SELLER";

export default function DealRoomPage() {
  const [workspace, setWorkspace] = useState<DealWorkspaceDTO | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [role, setRole] = useState<Role>("BUYER");
  const [accepting, setAccepting] = useState(false);
  const [accepted, setAccepted] = useState(false);

  const dealId = "deal-synth-001";

  useEffect(() => {
    let cancelled = false;
    fetchWorkspace(dealId)
      .then((ws) => {
        if (!cancelled) setWorkspace(ws);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [dealId]);

  async function handleAccept() {
    if (!workspace?.currentAgreement) return;
    setAccepting(true);
    setError(null);
    try {
      const snap = await acceptAgreement(
        workspace.dealId,
        workspace.currentAgreement.version,
        workspace.currentAgreement.termsHashSha256,
        role,
      );
      setAccepted(true);
      setWorkspace((prev) =>
        prev ? { ...prev, currentAgreement: snap } : prev,
      );
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setAccepting(false);
    }
  }

  if (error && !workspace) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-16">
        <h1 className="text-2xl font-semibold text-red-700">Deal Room</h1>
        <p className="mt-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
          Failed to load the deal: {error}
        </p>
      </main>
    );
  }

  if (!workspace) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-16">
        <h1 className="text-2xl font-semibold text-slate-900">Deal Room</h1>
        <p className="mt-4 text-slate-500" data-testid="loading">
          Loading deal {dealId}…
        </p>
      </main>
    );
  }

  const agreement = workspace.currentAgreement;
  const offers = workspace.offers;
  const latestOffer = offers[offers.length - 1];
  const openingOffer = offers[0];

  return (
    <main className="mx-auto max-w-4xl px-6 py-12">
      <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-emerald-700">
            Deal Room
          </p>
          <h1 className="text-2xl font-semibold text-slate-900">
            {agreement?.canonicalTerms.title ?? "Negotiation"}
          </h1>
        </div>
        <div className="flex items-center gap-3">
          <label htmlFor="role" className="text-sm text-slate-600">
            Viewing as
          </label>
          <select
            id="role"
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
            data-testid="role-select"
          >
            <option value="BUYER">Buyer</option>
            <option value="SELLER">Seller</option>
          </select>
        </div>
      </header>

      <section className="mb-8 grid gap-3 rounded-lg border border-slate-200 bg-white p-5 text-sm">
        <div className="flex justify-between">
          <span className="font-medium text-slate-500">Deal</span>
          <span data-testid="deal-id">{workspace.dealId}</span>
        </div>
        <div className="flex justify-between">
          <span className="font-medium text-slate-500">Status</span>
          <span data-testid="deal-status">{workspace.status}</span>
        </div>
        <div className="flex justify-between">
          <span className="font-medium text-slate-500">Buyer</span>
          <span>{workspace.buyerEmail}</span>
        </div>
        <div className="flex justify-between">
          <span className="font-medium text-slate-500">Seller</span>
          <span>{workspace.sellerEmail ?? "—"}</span>
        </div>
      </section>

      {latestOffer && openingOffer && (
        <section className="mb-8" data-testid="term-diff">
          <h2 className="mb-3 text-lg font-semibold text-slate-900">
            Term diff (round {openingOffer.roundNumber} → {latestOffer.roundNumber})
          </h2>
          <div className="overflow-hidden rounded-lg border border-slate-200">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-slate-600">
                <tr>
                  <th className="px-4 py-2 font-medium">Attribute</th>
                  <th className="px-4 py-2 font-medium">
                    Round {openingOffer.roundNumber} (opening)
                  </th>
                  <th className="px-4 py-2 font-medium">
                    Round {latestOffer.roundNumber} (latest)
                  </th>
                </tr>
              </thead>
              <tbody>
                {diffTerms(openingOffer.terms, latestOffer.terms).map(
                  (d: TermDiff) => (
                    <tr key={d.label} className="border-t border-slate-100">
                      <td className="px-4 py-2 font-medium">{d.label}</td>
                      <td className="px-4 py-2 text-slate-500">{d.from}</td>
                      <td
                        className={`px-4 py-2 font-medium ${
                          d.improved ? "text-emerald-700" : "text-red-700"
                        }`}
                      >
                        {d.to}
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {agreement && (
        <section
          className="mb-8 rounded-lg border border-slate-200 bg-white p-5"
          data-testid="agreement-card"
        >
          <h2 className="mb-3 text-lg font-semibold text-slate-900">
            Dual Agreement Signature
          </h2>
          <dl className="grid gap-3 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="font-medium text-slate-500">Version</dt>
              <dd data-testid="agreement-version">{agreement.version}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="font-medium text-slate-500">Price</dt>
              <dd data-testid="agreement-price">
                {formatMinorUnits(
                  agreement.canonicalTerms.priceMinorUnits,
                  agreement.canonicalTerms.currency,
                )}
              </dd>
            </div>
            <div>
              <dt className="font-medium text-slate-500">termsHashSha256</dt>
              <dd className="mt-1 break-all rounded bg-slate-50 px-3 py-2 font-mono text-xs" data-testid="terms-hash">
                {agreement.termsHashSha256}
              </dd>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div
                className={`rounded-md px-3 py-2 text-center text-sm font-medium ${
                  agreement.buyerAcceptedAt
                    ? "bg-emerald-50 text-emerald-800"
                    : "bg-slate-50 text-slate-500"
                }`}
                data-testid="buyer-acceptance"
              >
                Buyer: {agreement.buyerAcceptedAt ? "Signed" : "Pending"}
              </div>
              <div
                className={`rounded-md px-3 py-2 text-center text-sm font-medium ${
                  agreement.sellerAcceptedAt
                    ? "bg-emerald-50 text-emerald-800"
                    : "bg-slate-50 text-slate-500"
                }`}
                data-testid="seller-acceptance"
              >
                Seller: {agreement.sellerAcceptedAt ? "Signed" : "Pending"}
              </div>
            </div>
          </dl>
          <button
            type="button"
            onClick={handleAccept}
            disabled={accepting || accepted}
            className="mt-5 w-full rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
            data-testid="accept-button"
          >
            {accepting
              ? "Accepting…"
              : accepted
                ? "Accepted"
                : `Accept as ${role}`}
          </button>
          {error && (
            <p className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-700" data-testid="accept-error">
              {error}
            </p>
          )}
        </section>
      )}

      {offers.length > 0 && (
        <section data-testid="offer-history">
          <h2 className="mb-3 text-lg font-semibold text-slate-900">
            Offer history
          </h2>
          <ul className="space-y-3">
            {offers.map((o: OfferDTO) => (
              <li
                key={o.offerId}
                className="rounded-lg border border-slate-200 bg-white p-4 text-sm"
              >
                <div className="flex flex-wrap justify-between gap-2">
                  <span className="font-medium">
                    Round {o.roundNumber} — {o.proposerRole}
                  </span>
                  <span
                    className={
                      o.mandateCompliant
                        ? "text-emerald-700"
                        : "text-red-700"
                    }
                  >
                    {o.mandateCompliant ? "Mandate compliant" : "Non-compliant"}
                  </span>
                </div>
                <p className="mt-1 text-slate-500">{o.rationaleSummary}</p>
                <p className="mt-1 text-slate-600">
                  {formatMinorUnits(o.terms.priceMinorUnits, o.terms.currency)} ·{" "}
                  {o.terms.deliveryDays} days · {o.terms.includedRevisions}{" "}
                  revisions · utility {o.callerUtilityScore}
                </p>
              </li>
            ))}
          </ul>
        </section>
      )}
    </main>
  );
}
