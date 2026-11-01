import type { ReactNode } from "react";

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-50/70 px-5 py-8">
      <div className="space-y-1">
        <p className="text-sm font-semibold text-slate-800">{title}</p>
        <div className="max-w-2xl text-sm leading-relaxed text-slate-600">{description}</div>
      </div>
      {action}
    </div>
  );
}
