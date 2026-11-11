"use client";

import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/format";

type Variant = "primary" | "secondary" | "ghost" | "danger" | "success";
type Size = "sm" | "md";

const variants: Record<Variant, string> = {
  primary:
    "bg-slate-950 text-white shadow-sm hover:bg-slate-800 active:scale-[0.98] focus-visible:outline-slate-950 disabled:bg-slate-300 disabled:shadow-none",
  secondary:
    "border border-slate-200 bg-white text-slate-800 shadow-sm hover:bg-slate-50 hover:border-slate-300 active:scale-[0.98] focus-visible:outline-slate-500 disabled:bg-slate-100 disabled:text-slate-400",
  ghost:
    "text-slate-700 hover:bg-slate-100 hover:text-slate-900 active:scale-[0.98] focus-visible:outline-slate-500 disabled:text-slate-400",
  danger:
    "bg-rose-600 text-white shadow-sm hover:bg-rose-700 active:scale-[0.98] focus-visible:outline-rose-600 disabled:bg-rose-300",
  success:
    "bg-emerald-600 text-white shadow-sm hover:bg-emerald-700 active:scale-[0.98] focus-visible:outline-emerald-600 disabled:bg-emerald-300",
};

const sizes: Record<Size, string> = {
  sm: "min-h-8.5 px-3 py-1.5 text-xs rounded-lg",
  md: "min-h-10 px-4 py-2 text-sm rounded-xl",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  children: ReactNode;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  disabled,
  className,
  children,
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed",
        variants[variant],
        sizes[size],
        className,
      )}
      {...props}
    >
      {loading ? (
        <span
          aria-hidden="true"
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      ) : null}
      {children}
    </button>
  );
}
