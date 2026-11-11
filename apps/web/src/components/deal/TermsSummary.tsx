import type { StructuredTermsDTO } from "@/types/contracts";
import { formatMinorUnits } from "@/lib/format";

export function TermsSummary({ terms, compact = false }: { terms: StructuredTermsDTO; compact?: boolean }) {
  return (
    <div className={compact ? "space-y-3 text-sm" : "space-y-5 text-sm"}>
      <div className="flex flex-wrap items-baseline justify-between gap-3 border-b border-slate-100 pb-3">
        <h3 className={compact ? "text-base font-bold text-slate-900" : "text-lg font-bold text-slate-900"}>
          {terms.title}
        </h3>
        <p className="text-xl font-extrabold tracking-tight text-emerald-700 bg-emerald-50/80 px-3 py-1 rounded-xl border border-emerald-200/60">
          {formatMinorUnits(terms.priceMinorUnits, terms.currency)}
        </p>
      </div>

      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3 shadow-2xs">
          <dt className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Delivery Window</dt>
          <dd className="mt-1 text-sm font-bold text-slate-900">{terms.deliveryDays} calendar days</dd>
        </div>
        <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3 shadow-2xs">
          <dt className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Revisions Allowed</dt>
          <dd className="mt-1 text-sm font-bold text-slate-900">{terms.includedRevisions} rounds</dd>
        </div>
        <div className="rounded-xl border border-slate-100 bg-slate-50/70 p-3 shadow-2xs">
          <dt className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Settlement Currency</dt>
          <dd className="mt-1 text-sm font-bold text-slate-900">{terms.currency}</dd>
        </div>
      </dl>

      <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-4">
        <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Agreed Deliverables</p>
        <ul className="mt-2.5 space-y-2 text-slate-800">
          {terms.deliverables.map((deliverable) => (
            <li key={deliverable} className="flex items-start gap-2.5 text-xs font-medium">
              <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-[10px] font-bold text-emerald-800">
                ✓
              </span>
              <span>{deliverable}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="rounded-xl border border-slate-100 bg-slate-50/50 p-4">
        <p className="text-xs font-bold uppercase tracking-wider text-slate-500">Acceptance Criteria</p>
        <p className="mt-1.5 text-xs leading-relaxed text-slate-700 bg-white p-3 rounded-lg border border-slate-200/60 font-mono">
          {terms.acceptanceCriteriaText}
        </p>
      </div>
    </div>
  );
}
