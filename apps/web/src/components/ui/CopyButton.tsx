"use client";

import { useState } from "react";
import { Button } from "./Button";

export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const [state, setState] = useState<"idle" | "copied" | "error">("idle");

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setState("copied");
      window.setTimeout(() => setState("idle"), 2000);
    } catch {
      setState("error");
    }
  }

  return (
    <span className="inline-flex items-center gap-2">
      <Button variant="secondary" size="sm" onClick={copy}>
        {state === "copied" ? "Copied" : label}
      </Button>
      <span aria-live="polite" className="text-xs text-slate-500">
        {state === "error" ? "Copy failed — select the value manually." : null}
      </span>
    </span>
  );
}
