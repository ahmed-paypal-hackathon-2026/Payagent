import type { DealStatus } from "@/types/contracts";
import { cn } from "@/lib/format";

const stages = [
  { label: "Negotiate", statuses: ["DRAFT", "INVITED", "ACTIVE_NEGOTIATION"] },
  { label: "Agree", statuses: ["TERMS_PENDING_ACCEPTANCE"] },
  { label: "Authorize", statuses: ["AGREED", "SETTLEMENT_PENDING"] },
  { label: "Deliver", statuses: ["IN_PROGRESS", "DELIVERABLES_SUBMITTED", "ACCEPTED", "CLOSED"] },
];

export function StatusStepper({ status }: { status: DealStatus }) {
  const active = stages.findIndex(({ statuses }) => statuses.includes(status));

  return (
    <ol className="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-4" aria-label="Deal progress">
      {stages.map(({ label }, index) => {
        const complete = active > index || status === "CLOSED";
        const current = active === index && !complete;
        return (
          <li key={label} className="flex min-w-0 items-center gap-2" aria-current={current ? "step" : undefined}>
            <span
              aria-hidden="true"
              className={cn(
                "flex h-6 w-6 shrink-0 items-center justify-center rounded-full border text-xs",
                complete ? "border-emerald-200 bg-emerald-100 text-emerald-900" :
                  current ? "border-indigo-700 bg-indigo-700 text-white" : "border-slate-300 text-slate-600",
              )}
            >
              {complete ? "✓" : index + 1}
            </span>
            <span className={cn("text-xs sm:text-sm", current ? "font-bold text-indigo-950" : "font-medium text-slate-700")}>
              {label}{complete ? <span className="sr-only"> completed</span> : null}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
