import type { ReactNode } from "react";
import { cn } from "@/lib/format";

type AlertTone = "info" | "success" | "warning" | "error";

const tones: Record<AlertTone, string> = {
  info: "border-sky-200/90 bg-sky-50/70 text-sky-900 shadow-2xs",
  success: "border-emerald-200/90 bg-emerald-50/70 text-emerald-950 shadow-2xs",
  warning: "border-amber-200/90 bg-amber-50/70 text-amber-950 shadow-2xs",
  error: "border-rose-200/90 bg-rose-50/70 text-rose-950 shadow-2xs",
};

const icons: Record<AlertTone, string> = {
  info: "ℹ",
  success: "✓",
  warning: "⚠",
  error: "✕",
};

export function Alert({
  tone = "info",
  title,
  children,
  action,
  className,
}: {
  tone?: AlertTone;
  title?: ReactNode;
  children?: ReactNode;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={cn("flex items-start gap-3.5 rounded-2xl border px-4.5 py-3.5 text-sm", tones[tone], className)}
    >
      <span
        aria-hidden="true"
        className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-lg border border-current/20 bg-white/70 text-xs font-bold"
      >
        {icons[tone]}
      </span>
      <div className="min-w-0 flex-1 space-y-1">
        {title ? <p className="font-semibold tracking-tight">{title}</p> : null}
        {children ? <div className="text-xs leading-relaxed opacity-90">{children}</div> : null}
      </div>
      {action ? <div className="shrink-0 self-center">{action}</div> : null}
    </div>
  );
}
