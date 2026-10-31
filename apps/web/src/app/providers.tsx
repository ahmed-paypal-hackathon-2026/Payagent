"use client";

import type { ReactNode } from "react";
import { MockDataProvider } from "@/providers/MockDataProvider";
import { SessionProvider } from "@/providers/SessionProvider";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <SessionProvider>
      <MockDataProvider>{children}</MockDataProvider>
    </SessionProvider>
  );
}
