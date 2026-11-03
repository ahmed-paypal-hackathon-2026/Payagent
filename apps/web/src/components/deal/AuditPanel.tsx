"use client";

import { useMemo, useState } from "react";
import { formatDateTime } from "@/lib/format";
import type { StreamStatus } from "@/lib/api/live-events";
import { Badge } from "@/components/ui/Badge";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { EmptyState } from "@/components/ui/EmptyState";
import { Field, Input } from "@/components/ui/Field";
export interface AuditPanelEvent {
  dealId: string;
  seq: number;
  type: string;
  actorRole: string | null;
  summary: string;
  at: string;
  metadata: Record<string, string | number | boolean | null> | null;
}

function humanize(type: string): string {
  return type
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export function AuditPanel({
  events,
  mode,
  refreshing = false,
  onRefresh,
  streamStatus,
}: {
  events: AuditPanelEvent[];
  mode: "mock" | "live";
  refreshing?: boolean;
  onRefresh?: () => void;
  streamStatus?: StreamStatus;
}) {
  const live = mode === "live";
  const [afterSeq, setAfterSeq] = useState("0");
  const streamBadge =
    streamStatus === "live"
      ? { tone: "success" as const, label: "Live push" }
      : streamStatus === "connecting"
        ? { tone: "warning" as const, label: "Connecting push" }
        : undefined;
  const cursor = Number(afterSeq);
  const safeCursor = Number.isInteger(cursor) && cursor >= 0 ? cursor : 0;
  const filtered = useMemo(
    () => [...events].filter((event) => event.seq > safeCursor).sort((a, b) => a.seq - b.seq),
    [events, safeCursor],
  );

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader
          title={live ? "Audit timeline from the API" : "Audit timeline"}
          description={
            live
              ? "Sequenced events from GET /events with ?afterSeq cursoring; role-scoped sanitization runs on the server."
              : "Sequenced events recorded by the demo engine."
          }
          action={
            <div className="flex flex-wrap items-center gap-1.5">
              {streamBadge ? <Badge tone={streamBadge.tone}>{streamBadge.label}</Badge> : null}
              <Badge tone="neutral">{filtered.length} shown</Badge>
            </div>
          }
        />
        <CardBody className="space-y-4">
          <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
            <Field
              label="Replay after sequence"
              htmlFor="audit-cursor"
              hint="Mirrors the ?afterSeq= cursor concept."
              className="w-40"
            >
              <Input
                id="audit-cursor"
                type="number"
                min={0}
                step={1}
                value={afterSeq}
                onChange={(event) => setAfterSeq(event.target.value)}
              />
            </Field>
            <button
              type="button"
              onClick={() => setAfterSeq("0")}
              className="mb-0.5 text-xs font-semibold text-slate-600 underline underline-offset-2 hover:text-slate-900"
            >
              Reset cursor
            </button>
          </div>

          {filtered.length === 0 ? (
            <EmptyState
              title="No events after this cursor"
              description={
                live
                  ? "Lower the cursor or refresh from the API to load newer events."
                  : "Reset the cursor to see the full retained timeline. The demo never invents events for actions that did not run."
              }
            />
          ) : (
            <ol className="relative space-y-4 border-l border-slate-200 pl-5">
              {filtered.map((event) => (
                <li key={`${event.dealId}-${event.seq}`} className="relative">
                  <span
                    aria-hidden="true"
                    className="absolute -left-[1.4rem] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-white bg-emerald-600"
                  />
                  <article className="rounded-xl border border-slate-200 bg-white px-4 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <Badge tone="neutral">#{event.seq}</Badge>
                        <span className="text-sm font-semibold text-slate-900">{humanize(event.type)}</span>
                        {event.actorRole ? (
                          <Badge tone={event.actorRole === "BUYER" ? "info" : "accent"}>{event.actorRole}</Badge>
                        ) : null}
                      </div>
                      <time className="text-[11px] text-slate-500">{formatDateTime(event.at)}</time>
                    </div>
                    {event.summary ? (
                      <p className="mt-1 text-sm leading-relaxed text-slate-700">{event.summary}</p>
                    ) : null}
                    {event.metadata && Object.keys(event.metadata).length ? (
                      <dl className="mt-2 flex flex-wrap gap-1.5">
                        {Object.entries(event.metadata).map(([key, value]) => (
                          <div
                            key={key}
                            className="rounded-md bg-slate-100 px-2 py-0.5 text-[11px] text-slate-700"
                          >
                            <dt className="inline font-semibold">{key}: </dt>
                            <dd className="inline font-mono">{String(value)}</dd>
                          </div>
                        ))}
                      </dl>
                    ) : null}
                  </article>
                </li>
              ))}
            </ol>
          )}

          {live && onRefresh ? (
            <div className="flex justify-end">
              <button
                type="button"
                onClick={onRefresh}
                disabled={refreshing}
                className="text-xs font-semibold text-slate-600 underline underline-offset-2 hover:text-slate-900 disabled:opacity-50"
              >
                {refreshing ? "Refreshing…" : "Refresh timeline from the API"}
              </button>
            </div>
          ) : null}
        </CardBody>
      </Card>

      <p className="text-xs leading-relaxed text-slate-500">
        {live
          ? "Events include only your role's sanitized view of the payload. Private mandate limits are masked on the server."
          : "Audit events never include the counterparty mandate, limits, targets, or forbidden terms."}
      </p>
    </div>
  );
}
