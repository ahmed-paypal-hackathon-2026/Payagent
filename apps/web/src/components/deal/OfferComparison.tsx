import type { OfferDTO, PartyRole } from "@/types/contracts";
import { formatDateTime, formatMinorUnits, isNumericUtilityScore } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";

function priceDelta(current: OfferDTO, previous: OfferDTO | null): string | null {
  if (!previous) return null;
  const delta = current.terms.priceMinorUnits - previous.terms.priceMinorUnits;
  if (delta === 0) return "No change";
  const sign = delta > 0 ? "+" : "−";
  return `${sign}${formatMinorUnits(Math.abs(delta), current.terms.currency)}`;
}

function roleLabel(role: PartyRole): string {
  return role === "BUYER" ? "Buyer" : "Seller";
}

export function OfferComparison({ offers, myRole }: { offers: OfferDTO[]; myRole: PartyRole }) {
  if (offers.length === 0) {
    return (
      <EmptyState
        title="No offers yet"
        description="The first offer establishes the round history. Both parties can see the shared terms; private mandates and the counterparty's utility stay hidden."
      />
    );
  }

  const sorted = [...offers].sort((a, b) => a.roundNumber - b.roundNumber);
  const latestRound = sorted[sorted.length - 1]?.roundNumber;

  return (
    <div>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full border-collapse text-left text-sm">
          <caption className="sr-only">Offer comparison across negotiation rounds</caption>
          <thead>
            <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
              <th scope="col" className="py-2 pr-3 font-semibold">Round</th>
              <th scope="col" className="py-2 pr-3 font-semibold">Proposed by</th>
              <th scope="col" className="py-2 pr-3 font-semibold">Price</th>
              <th scope="col" className="py-2 pr-3 font-semibold">Delivery</th>
              <th scope="col" className="py-2 pr-3 font-semibold">Revisions</th>
              <th scope="col" className="py-2 pr-3 font-semibold">Your score</th>
              <th scope="col" className="py-2 font-semibold">Your check</th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((offer, index) => {
              const own = offer.proposerRole === myRole;
              const delta = priceDelta(offer, index > 0 ? sorted[index - 1] : null);
              return (
                <tr
                  key={offer.offerId}
                  className={`border-b border-slate-100 align-top ${offer.roundNumber === latestRound ? "bg-emerald-50/40" : ""}`}
                >
                  <td className="py-3 pr-3">
                    <div className="flex flex-col gap-1">
                      <span className="font-semibold text-slate-900">#{offer.roundNumber}</span>
                      {offer.roundNumber === latestRound ? <Badge tone="success">Latest</Badge> : null}
                    </div>
                  </td>
                  <td className="py-3 pr-3">
                    <div className="flex flex-col gap-1">
                      <span className={own ? "font-semibold text-slate-900" : "text-slate-700"}>
                        {roleLabel(offer.proposerRole)}
                        {own ? " (you)" : ""}
                      </span>
                      {offer.generatedByAgent ? <Badge tone="accent">Agent</Badge> : null}
                      <span className="text-xs text-slate-500">{formatDateTime(offer.createdAt)}</span>
                    </div>
                  </td>
                  <td className="py-3 pr-3">
                    <div className="flex flex-col gap-0.5">
                      <span className="font-semibold text-slate-900">
                        {formatMinorUnits(offer.terms.priceMinorUnits, offer.terms.currency)}
                      </span>
                      {delta ? <span className="text-xs text-slate-500">{delta}</span> : null}
                    </div>
                  </td>
                  <td className="py-3 pr-3 text-slate-700">{offer.terms.deliveryDays} days</td>
                  <td className="py-3 pr-3 text-slate-700">{offer.terms.includedRevisions}</td>
                  <td className="py-3 pr-3">
                    {own ? (
                      isNumericUtilityScore(offer.callerUtilityScore) ? (
                        <span className="font-mono text-xs font-semibold text-slate-800">{offer.callerUtilityScore}</span>
                      ) : (
                        <Badge tone="warning">Demo — not scored</Badge>
                      )
                    ) : (
                      <span className="text-xs text-slate-400" title="Counterparty utility is not shown in this view.">
                        Private
                      </span>
                    )}
                  </td>
                  <td className="py-3">
                    {own ? (
                      offer.mandateCompliant ? (
                        <Badge tone="success">Within mandate</Badge>
                      ) : (
                        <Badge tone="danger">Outside mandate</Badge>
                      )
                    ) : (
                      <span className="text-xs text-slate-400" title="Counterparty mandate compliance is not shown in this view.">
                        Private
                      </span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <div className="space-y-3 md:hidden">
        {sorted.map((offer, index) => {
          const own = offer.proposerRole === myRole;
          const delta = priceDelta(offer, index > 0 ? sorted[index - 1] : null);
          return (
            <article
              key={offer.offerId}
              className={`rounded-xl border p-3 ${
                offer.roundNumber === latestRound ? "border-emerald-300 bg-emerald-50/40" : "border-slate-200 bg-white"
              }`}
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-sm font-semibold text-slate-900">
                  Round #{offer.roundNumber} · {roleLabel(offer.proposerRole)}
                  {own ? " (you)" : ""}
                </p>
                <div className="flex flex-wrap gap-1">
                  {offer.roundNumber === latestRound ? <Badge tone="success">Latest</Badge> : null}
                  {offer.generatedByAgent ? <Badge tone="accent">Agent</Badge> : null}
                </div>
              </div>
              <p className="mt-2 text-lg font-bold text-slate-900">
                {formatMinorUnits(offer.terms.priceMinorUnits, offer.terms.currency)}
                {delta ? <span className="ml-2 text-xs font-medium text-slate-500">{delta}</span> : null}
              </p>
              <dl className="mt-2 grid grid-cols-2 gap-2 text-xs">
                <div>
                  <dt className="text-slate-500">Delivery</dt>
                  <dd className="font-semibold text-slate-800">{offer.terms.deliveryDays} days</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Revisions</dt>
                  <dd className="font-semibold text-slate-800">{offer.terms.includedRevisions}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Your score</dt>
                  <dd className="font-semibold text-slate-800">
                    {own
                      ? isNumericUtilityScore(offer.callerUtilityScore)
                        ? offer.callerUtilityScore
                        : "Demo — not scored"
                      : "Private"}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-500">Your check</dt>
                  <dd className="font-semibold text-slate-800">
                    {own ? (offer.mandateCompliant ? "Within mandate" : "Outside mandate") : "Private"}
                  </dd>
                </div>
              </dl>
              {offer.rationaleSummary ? (
                <p className="mt-2 border-t border-slate-200/70 pt-2 text-xs leading-relaxed text-slate-600">
                  {offer.rationaleSummary}
                </p>
              ) : null}
              <p className="mt-1 text-[11px] text-slate-400">{formatDateTime(offer.createdAt)}</p>
            </article>
          );
        })}
      </div>

      <p className="mt-3 text-xs leading-relaxed text-slate-500">
        Scores and mandate checks are rendered only for offers you proposed. The comparison never exposes the other
        party&apos;s private mandate, target, or utility.
      </p>
    </div>
  );
}
