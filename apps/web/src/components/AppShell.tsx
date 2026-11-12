"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { cn } from "@/lib/format";
import { useSession } from "@/providers/SessionProvider";
import { DemoUserSwitcher } from "./DemoUserSwitcher";

const navItems = [
  { href: "/", label: "Deals" },
  { href: "/intake", label: "New deal" },
  { href: "/join", label: "Join deal" },
];

const navLink = "rounded-lg px-3 py-2 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-violet-300";
const inactive = "text-violet-100 hover:bg-white/10";

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { mode, liveSession, logout } = useSession();

  return (
    <div className="min-h-screen bg-[#faf6ef] text-[#181529] lg:grid lg:grid-cols-[12rem_minmax(0,1fr)]">
      <header className="flex flex-wrap items-center justify-between gap-3 bg-[#141128] px-5 py-4 text-white lg:sticky lg:top-0 lg:h-screen lg:flex-col lg:items-stretch lg:justify-start lg:gap-8 lg:overflow-y-auto lg:py-7">
        <Link href="/" className="rounded font-serif text-3xl tracking-tight focus-visible:outline focus-visible:outline-2 focus-visible:outline-violet-300">
          Payagent
        </Link>
        <nav aria-label="Primary" className="flex flex-wrap gap-1 lg:flex-col">
          {navItems.map(({ href, label }) => {
            const active = pathname === href || (href === "/" && pathname.startsWith("/deals/"));
            return (
              <Link key={href} href={href} aria-current={active ? "page" : undefined} className={cn(navLink, active ? "bg-[#393368] text-white" : inactive)}>
                {label}
              </Link>
            );
          })}
        </nav>
        <div className="w-full lg:mt-auto [&_label]:flex-wrap [&_label]:text-violet-100 [&_select]:w-full">
          {mode === "mock" ? (
            <DemoUserSwitcher />
          ) : liveSession ? (
            <div className="space-y-2">
              <p className="truncate px-3 text-xs text-violet-200" title={liveSession.email}>{liveSession.email}</p>
              <button type="button" onClick={logout} className={cn(navLink, inactive)}>Sign out</button>
            </div>
          ) : (
            <Link href="/login" aria-current={pathname === "/login" ? "page" : undefined} className={cn("inline-flex", navLink, pathname === "/login" ? "bg-[#393368] text-white" : inactive)}>
              Sign in
            </Link>
          )}
        </div>
      </header>
      <main className="mx-auto w-full min-w-0 max-w-7xl px-4 py-6 sm:px-8 lg:px-10 lg:py-9">
        <p className="mb-4 text-xs font-bold uppercase tracking-[0.2em] text-indigo-800">
          PayPal Sandbox{mode === "mock" ? " · Simulation" : ""}
        </p>
        {children}
      </main>
    </div>
  );
}
