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
    <div className="flex min-h-screen flex-col bg-slate-50/70 antialiased selection:bg-emerald-100 selection:text-emerald-900">
      <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/80 backdrop-blur-md shadow-[0_1px_3px_0_rgba(0,0,0,0.02)]">
        <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-x-6 gap-y-3 px-4 py-3 sm:px-6 lg:px-8">
          <div className="flex items-center gap-6">
            <Link
              href="/"
              className="group flex items-center gap-3 rounded-xl p-1 transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-600"
            >
              <div className="relative flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br from-[#003087] via-[#0070ba] to-[#0079c1] text-white shadow-md shadow-sky-900/20 transition-all duration-300 group-hover:scale-105 group-hover:shadow-lg group-hover:shadow-sky-500/25">
                <span className="font-black text-lg tracking-tight italic select-none">P</span>
                <span className="absolute -top-0.5 -right-0.5 flex h-2.5 w-2.5">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-cyan-400" />
                </span>
              </div>
              <span className="flex flex-col leading-tight">
                <span className="flex items-center gap-1.5 text-base font-extrabold tracking-tight text-slate-900">
                  <span className="text-[#003087]">Pay</span>
                  <span className="text-[#0070ba]">agent</span>
                  <span className="rounded-md bg-sky-100/80 px-1.5 py-0.5 text-[9px] font-extrabold uppercase tracking-wider text-[#003087]">
                    Sandbox
                  </span>
                </span>
                <span className="text-[11px] font-medium text-slate-500">Autonomous Bilateral Deal Room</span>
              </span>
            </Link>

            <nav aria-label="Primary" className="hidden items-center gap-1 rounded-xl bg-slate-100/80 p-1 sm:flex">
              {navItems.map((item) => {
                const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={cn(
                      "rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600",
                      active
                        ? "bg-white text-slate-950 shadow-sm"
                        : "text-slate-600 hover:text-slate-900 hover:bg-white/50",
                    )}
                  >
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            <span
              className={cn(
                "hidden items-center gap-1.5 rounded-full border px-3 py-1 text-[11px] font-semibold tracking-tight sm:inline-flex",
                mode === "mock"
                  ? "border-violet-200/80 bg-violet-50/70 text-violet-700 shadow-sm"
                  : "border-emerald-200/80 bg-emerald-50/70 text-emerald-800 shadow-sm",
              )}
            >
              <span
                className={cn(
                  "h-1.5 w-1.5 rounded-full",
                  mode === "mock" ? "bg-violet-500 animate-pulse" : "bg-emerald-600",
                )}
              />
              {mode === "mock" ? "Demo simulation" : "Live API"}
            </span>

            {mode === "mock" ? (
              <DemoUserSwitcher />
            ) : liveSession ? (
              <div className="flex items-center gap-2">
                <span className="hidden max-w-[14rem] truncate text-xs font-medium text-slate-700 md:inline">
                  {liveSession.email}
                </span>
                <button
                  type="button"
                  onClick={logout}
                  className="rounded-lg px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-100 hover:text-slate-900 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
                >
                  Sign out
                </button>
              </div>
            ) : (
              <Link
                href="/login"
                className="rounded-xl bg-slate-900 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-slate-800 transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
              >
                Sign in
              </Link>
            )}
          </div>
        </div>

        {/* Mobile navigation bar */}
        <div className="flex items-center gap-1 border-t border-slate-100 px-4 py-2 sm:hidden overflow-x-auto">
          {navItems.map((item) => {
            const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-semibold transition",
                  active ? "bg-slate-900 text-white" : "text-slate-600 hover:bg-slate-100",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </div>

        <ModeBanner />
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 lg:px-8">{children}</main>

      <footer className="border-t border-slate-200/80 bg-white">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-6 text-xs leading-relaxed text-slate-500 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
          <div className="flex items-center gap-2">
            <span className="flex h-5 w-5 items-center justify-center rounded-md bg-slate-100 text-[10px] font-bold text-slate-700">
              P
            </span>
            <span>
              Payagent — PayPal Sandbox Bilateral Escrow & Automated Settlement.
            </span>
          </div>
          <p className="max-w-xl text-slate-400">
            PayPal integration operates strictly in sandbox mode. The browser client never calls PayPal directly and never computes authoritative utility or financial state.
          </p>
        </div>
      </footer>
    </div>
  );
}
