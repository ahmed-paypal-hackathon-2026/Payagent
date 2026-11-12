import type { DealStatus } from "@/types/contracts";
import { cn } from "@/lib/format";

const steps = [
  { id: "negotiation", label: "Negotiate", statuses: ["DRAFT", "INVITED", "ACTIVE_NEGOTIATION"] },
  { id: "terms", label: "Terms", statuses: ["TERMS_PENDING_ACCEPTANCE"] },
  { id: "agreed", label: "Agreed", statuses: ["AGREED"] },
  { id: "settlement", label: "Settlement", statuses: ["SETTLEMENT_PENDING", "IN_PROGRESS"] },
  { id: "delivery", label: "Deliverables", statuses: ["DELIVERABLES_SUBMITTED", "ACCEPTED"] },
  { id: "closed", label: "Closed", statuses: ["CLOSED"] },
] as const;

function activeStepIndex(status: DealStatus): number {
  if (status === "DISPUTED" || status === "CANCELLED") return -1;
  const index = steps.findIndex((step) => (step.statuses as readonly string[]).includes(status));
  return index < 0 ? 0 : index;
}

export function StatusStepper({ status }: { status: DealStatus }) {
  const active = activeStepIndex(status);
  const special = status === "DISPUTED" || status === "CANCELLED";

  return (
    <div className="w-full">
      <ol className="flex w-full items-center justify-between gap-2 overflow-x-auto pb-2 scrollbar-thin" aria-label="Deal progress">
        {steps.map((step, index) => {
          const complete = !special && active > index;
          const current = !special && active === index;
          return (
            <li key={step.id} className="relative flex flex-1 min-w-[5.5rem] items-center">
              <div
                className={cn(
                  "flex w-full flex-col items-center gap-1.5 rounded-xl p-2 text-center transition-all",
                  current && "bg-emerald-50/80 ring-1 ring-emerald-300 shadow-2xs",
                  complete && "opacity-90",
                  special && "opacity-60",
                )}
                aria-current={current ? "step" : undefined}
              >
                <div className="relative">
                  <span
                    aria-hidden="true"
                    className={cn(
                      "flex h-7 w-7 items-center justify-center rounded-xl text-xs font-bold transition-all shadow-2xs",
                      complete && "bg-emerald-600 text-white shadow-emerald-500/20",
                      current && "bg-slate-900 text-emerald-400 ring-2 ring-emerald-500",
                      !complete && !current && "bg-slate-100 text-slate-400 border border-slate-200/60",
                    )}
                  >
                    {complete ? "✓" : index + 1}
                  </span>
                  {current ? (
                    <span className="absolute -top-1 -right-1 flex h-2.5 w-2.5">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                      <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500" />
                    </span>
                  ) : null}
                </div>
                <span
                  className={cn(
                    "text-[11px] font-bold tracking-tight whitespace-nowrap",
                    current ? "text-slate-900" : complete ? "text-emerald-900" : "text-slate-400",
                  )}
                >
                  {step.label}
                </span>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
