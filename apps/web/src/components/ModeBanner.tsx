"use client";

import Link from "next/link";
import { useSession } from "@/providers/SessionProvider";

export function ModeBanner() {
  const { mode, apiBaseUrl } = useSession();

  if (mode === "mock") {
    return (
      <div className="border-t border-violet-100 bg-violet-50/70">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-x-2 gap-y-1 px-4 py-1.5 text-[11px] leading-relaxed text-violet-900 sm:px-6 lg:px-8">
          <span className="font-bold uppercase tracking-wide">Demo mode</span>
          <span>
            Synthetic data only. Workspace actions run in a local demo engine; no backend, PayPal, or LLM calls are made.
          </span>
        </div>
      </div>
    );
  }

  return (
    <div className="border-t border-sky-100 bg-sky-50/70">
      <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center gap-x-2 gap-y-1 px-4 py-1.5 text-[11px] leading-relaxed text-sky-900 sm:px-6 lg:px-8">
        <span className="font-bold uppercase tracking-wide">Live API</span>
        <span>
          Requests go only to <code className="rounded bg-white/70 px-1">{apiBaseUrl}</code>. No PayPal calls are made from
          the browser.
        </span>
        <Link href="/login" className="font-semibold underline underline-offset-2">
          Session
        </Link>
      </div>
    </div>
  );
}
