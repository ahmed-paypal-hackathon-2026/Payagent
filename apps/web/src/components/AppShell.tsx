"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { cn } from "@/lib/format";
import { useSession } from "@/providers/SessionProvider";
import { DemoUserSwitcher } from "./DemoUserSwitcher";
import { ModeBanner } from "./ModeBanner";

const navItems = [
  { href: "/", label: "Dashboard" },
  { href: "/intake", label: "New deal" },
  { href: "/join", label: "Join deal" },
];

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { mode, liveSession, logout } = useSession();

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:px-6 lg:px-8">
          <Link href="/" className="flex items-center gap-2.5 rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700">
            <span aria-hidden="true" className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-900 text-sm font-black text-white">
              P
            </span>
            <span className="flex flex-col leading-tight">
              <span className="text-sm font-bold tracking-tight text-slate-900">Payagent</span>
              <span className="text-[10px] font-medium uppercase tracking-wider text-slate-500">Bilateral deal room</span>
            </span>
          </Link>

          <nav aria-label="Primary" className="order-3 flex w-full items-center gap-1 overflow-x-auto sm:order-none sm:w-auto">
            {navItems.map((item) => {
              const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-semibold transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700",
                    active ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900",
                  )}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <span
              className={cn(
                "hidden rounded-full border px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wide sm:inline-flex",
                mode === "mock"
                  ? "border-violet-200 bg-violet-50 text-violet-800"
                  : "border-sky-200 bg-sky-50 text-sky-800",
              )}
            >
              {mode === "mock" ? "Demo data" : "Live API"}
            </span>
            {mode === "mock" ? (
              <DemoUserSwitcher />
            ) : liveSession ? (
              <div className="flex items-center gap-2">
                <span className="hidden max-w-[14rem] truncate text-xs font-medium text-slate-600 md:inline">
                  {liveSession.email}
                </span>
                <button
                  type="button"
                  onClick={logout}
                  className="rounded-lg px-2.5 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
                >
                  Sign out
                </button>
              </div>
            ) : (
              <Link
                href="/login"
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-slate-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
              >
                Sign in
              </Link>
            )}
          </div>
        </div>
        <ModeBanner />
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-7 sm:px-6 lg:px-8">{children}</main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-2 px-4 py-5 text-xs leading-relaxed text-slate-500 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
          <p>
            PayPal integration is sandbox-only. The browser never calls PayPal directly and never computes authoritative
            utility, mandate compliance, or payment state.
          </p>
          <p className="shrink-0 font-medium text-slate-600">Member D · Next.js workspace</p>
        </div>
      </footer>
    </div>
  );
}
