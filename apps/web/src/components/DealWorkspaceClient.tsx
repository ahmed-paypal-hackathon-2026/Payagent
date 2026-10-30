"use client";

import { Alert } from "@/components/ui/Alert";
import { MockDealWorkspace } from "@/components/deal/MockDealWorkspace";
import { LiveDealConsole } from "@/components/deal/LiveDealConsole";
import { useSession } from "@/providers/SessionProvider";

export function DealWorkspaceClient({ dealId }: { dealId: string }) {
  const { mode, ready } = useSession();

  if (!ready) return <Alert tone="info">Loading workspace…</Alert>;
  return mode === "mock" ? <MockDealWorkspace dealId={dealId} /> : <LiveDealConsole dealId={dealId} />;
}
