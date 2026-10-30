import type { StructuredTermsDTO } from "@/types/contracts";
import { formatMinorUnits } from "@/lib/format";

export function TermsSummary({ terms, compact = false }: { terms: StructuredTermsDTO; compact?: boolean }) {
  return (
    <div className={compact ? "space-y-2 text-sm" : "space-y-4 text-sm"}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className={compact ? "text-sm font-semibold text-slate-900" : "text-base font-semibold text-slate-900"}>
          {terms.title}
        </h3>
        <p className="text-base font-bold text-slate-900">{formatMinorUnits(terms.priceMinorUnits, terms.currency)}</p>
      </div>

      <dl className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-3">
        <div className="rounded-lg bg-slate-50 px-2.5 py-1.5">
          <dt className="font-medium text-slate-500">Delivery</dt>
          <dd className="font-semibold text-slate-900">{terms.deliveryDays} days</dd>
        </div>
        <div className="rounded-lg bg-slate-50 px-2.5 py-1.5">
          <dt className="font-medium text-slate-500">Revisions</dt>
          <dd className="font-semibold text-slate-900">{terms.includedRevisions}</dd>
        </div>
        <div className="rounded-lg bg-slate-50 px-2.5 py-1.5">
          <dt className="font-medium text-slate-500">Currency</dt>
          <dd className="font-semibold text-slate-900">{terms.currency}</dd>
        </div>
      </dl>

      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Deliverables</p>
        <ul className="mt-1 list-disc space-y-1 pl-5 text-slate-700">
          {terms.deliverables.map((deliverable) => (
            <li key={deliverable}>{deliverable}</li>
          ))}
        </ul>
      </div>

      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Acceptance criteria</p>
        <p className="mt-1 leading-relaxed text-slate-700">{terms.acceptanceCriteriaText}</p>
      </div>
    </div>
  );
}
