import type { ReactNode } from "react";
import { cn } from "@/lib/format";

export type BadgeTone = "neutral" | "info" | "success" | "warning" | "danger" | "accent";

const tones: Record<BadgeTone, string> = {
  neutral: "border-slate-200/90 bg-slate-50 text-slate-700",
  info: "border-sky-200/80 bg-sky-50/70 text-sky-800",
  success: "border-emerald-200/80 bg-emerald-50/80 text-emerald-800",
  warning: "border-amber-200/80 bg-amber-50/80 text-amber-900",
  danger: "border-rose-200/80 bg-rose-50/80 text-rose-800",
  accent: "border-violet-200/80 bg-violet-50/80 text-violet-800",
};

export function Badge({
  tone = "neutral",
  dot = false,
  className,
  children,
}: {
  tone?: BadgeTone;
  dot?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-semibold tracking-tight transition-colors shadow-2xs",
        tones[tone],
        className,
      )}
    >
      {dot ? <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-current opacity-80" /> : null}
      {children}
    </span>
  );
}
