import type { MandateDTO } from "@/types/contracts";
import { formatDateTime, formatMinorUnits } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";

export function MandateSummary({
  mandate,
  onEdit,
  editing = false,
}: {
  mandate: MandateDTO;
  onEdit?: () => void;
  editing?: boolean;
}) {
  const buyer = mandate.partyRole === "BUYER";

  return (
    <Card className="border-emerald-200">
      <CardHeader
        title="Your private mandate"
        description="This card is scoped to your authenticated identity. The counterparty never receives these values."
        action={
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge tone="success">v{mandate.version}</Badge>
            <Badge tone="accent">{mandate.mode === "AUTONOMOUS_BOUNDED" ? "Autonomous" : "Assisted"}</Badge>
          </div>
        }
      />
      <CardBody className="space-y-4">
        <dl className="grid grid-cols-2 gap-2 text-xs sm:grid-cols-3">
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <dt className="font-medium text-slate-500">{buyer ? "Maximum price" : "Minimum price"}</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">
              {formatMinorUnits(mandate.limitPriceMinorUnits, mandate.currency)}
            </dd>
          </div>
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <dt className="font-medium text-slate-500">Target price</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">
              {formatMinorUnits(mandate.targetPriceMinorUnits, mandate.currency)}
            </dd>
          </div>
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <dt className="font-medium text-slate-500">Max rounds</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">{mandate.maxRounds}</dd>
          </div>
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <dt className="font-medium text-slate-500">Delivery limit</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">{mandate.limitDeliveryDays} days</dd>
          </div>
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <dt className="font-medium text-slate-500">Delivery target</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">{mandate.targetDeliveryDays} days</dd>
          </div>
          <div className="rounded-lg bg-slate-50 px-2.5 py-2">
            <dt className="font-medium text-slate-500">Revisions</dt>
            <dd className="mt-0.5 font-semibold text-slate-900">
              min {mandate.limitRevisions} · target {mandate.targetRevisions}
            </dd>
          </div>
        </dl>

        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Must-have deliverables</p>
            {mandate.mustHaveDeliverables.length ? (
              <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-slate-700">
                {mandate.mustHaveDeliverables.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            ) : (
              <p className="mt-1 text-sm text-slate-500">None recorded.</p>
            )}
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Forbidden terms</p>
            {mandate.forbiddenTerms.length ? (
              <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-slate-700">
                {mandate.forbiddenTerms.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            ) : (
              <p className="mt-1 text-sm text-slate-500">None recorded.</p>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-3">
          <p className="text-xs text-slate-500">Expires {formatDateTime(mandate.expiresAt)}</p>
          {onEdit ? (
            <Button variant="secondary" size="sm" onClick={onEdit}>
              {editing ? "Close mandate editor" : "Update mandate"}
            </Button>
          ) : null}
        </div>
      </CardBody>
    </Card>
  );
}
