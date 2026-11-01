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
    <ol className="flex w-full items-center gap-1 overflow-x-auto pb-1" aria-label="Deal progress">
      {steps.map((step, index) => {
        const complete = !special && active > index;
        const current = !special && active === index;
        return (
          <li key={step.id} className="flex min-w-0 flex-1 items-center gap-1">
            <div
              className={cn(
                "flex min-w-[4.5rem] flex-1 flex-col gap-1 rounded-lg px-2 py-1.5 text-center",
                current && "bg-emerald-50",
                special && "opacity-60",
              )}
              aria-current={current ? "step" : undefined}
            >
              <span
                aria-hidden="true"
                className={cn(
                  "mx-auto flex h-5 w-5 items-center justify-center rounded-full border text-[10px] font-bold",
                  complete && "border-emerald-700 bg-emerald-700 text-white",
                  current && "border-emerald-700 bg-white text-emerald-800",
                  !complete && !current && "border-slate-300 bg-white text-slate-400",
                )}
              >
                {complete ? "✓" : index + 1}
              </span>
              <span
                className={cn(
                  "truncate text-[11px] font-semibold",
                  current ? "text-emerald-800" : complete ? "text-slate-700" : "text-slate-400",
                )}
              >
                {step.label}
              </span>
            </div>
            {index < steps.length - 1 ? (
              <span aria-hidden="true" className={cn("h-px w-2 shrink-0", complete ? "bg-emerald-600" : "bg-slate-300")} />
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
