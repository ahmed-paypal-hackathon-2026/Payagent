"use client";

import { useEffect, useId, useState } from "react";
import type { MandateDTO, PartyRole } from "@/types/contracts";
import type { MandateInput } from "@/lib/mock/types";
import {
  draftFromMandate,
  mandateFromDraft,
  nextFortnightLocal,
  type FieldErrors,
  type MandateDraft,
} from "@/lib/forms";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select, Textarea } from "@/components/ui/Field";

export function MandateBuilderForm({
  initial,
  role,
  onSubmit,
  submitLabel = "Lock mandate",
  disabled = false,
  disabledReason,
}: {
  initial: MandateDTO | null;
  role: PartyRole;
  onSubmit: (input: MandateInput) => Promise<void>;
  submitLabel?: string;
  disabled?: boolean;
  disabledReason?: string;
}) {
  const formId = useId().replace(/:/g, "");
  const [draft, setDraft] = useState<MandateDraft>(() => draftFromMandate(initial, role));
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    setDraft(draftFromMandate(initial, role));
  }, [initial, role]);

  useEffect(() => {
    setDraft((current) => (current.expiresAtLocal ? current : { ...current, expiresAtLocal: nextFortnightLocal() }));
  }, []);

  const set = (patch: Partial<MandateDraft>) => setDraft((current) => ({ ...current, ...patch }));

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const parsed = mandateFromDraft(draft, role);
    setErrors(parsed.errors);
    if (!parsed.value) {
      setFormError("Review the highlighted mandate fields before locking.");
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit(parsed.value);
      setErrors({});
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "The mandate could not be saved.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5" noValidate>
      <Alert tone="info" title="Private to you">
        Your limits, targets, must-have deliverables, and forbidden terms are never sent to the counterparty or placed in
        the URL. In live mode the server stores them against your authenticated identity.
      </Alert>

      {disabled && disabledReason ? <Alert tone="warning">{disabledReason}</Alert> : null}
      {formError ? <Alert tone="error">{formError}</Alert> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Currency" htmlFor={`${formId}-currency`} required>
          <Select
            id={`${formId}-currency`}
            value={draft.currency}
            onChange={(event) => set({ currency: event.target.value })}
            disabled={disabled}
          >
            <option value="USD">USD</option>
            <option value="EUR">EUR</option>
            <option value="GBP">GBP</option>
          </Select>
        </Field>

        <Field
          label="Negotiation mode"
          htmlFor={`${formId}-mode`}
          hint="Autonomous mode still stays inside this mandate."
        >
          <Select
            id={`${formId}-mode`}
            value={draft.mode}
            onChange={(event) => set({ mode: event.target.value as MandateDraft["mode"] })}
            disabled={disabled}
          >
            <option value="ASSISTED">Assisted</option>
            <option value="AUTONOMOUS_BOUNDED">Autonomous (bounded)</option>
          </Select>
        </Field>

        <Field
          label={role === "BUYER" ? "Maximum price" : "Minimum price"}
          htmlFor={`${formId}-limit-price`}
          hint={role === "BUYER" ? "You will not accept an offer above this." : "You will not accept an offer below this."}
          error={errors.limitPrice}
          required
        >
          <Input
            id={`${formId}-limit-price`}
            inputMode="decimal"
            value={draft.limitPriceInput}
            onChange={(event) => set({ limitPriceInput: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.limitPrice)}
            placeholder="1000.00"
          />
        </Field>

        <Field label="Target price" htmlFor={`${formId}-target-price`} error={errors.targetPrice} required>
          <Input
            id={`${formId}-target-price`}
            inputMode="decimal"
            value={draft.targetPriceInput}
            onChange={(event) => set({ targetPriceInput: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.targetPrice)}
            placeholder="900.00"
          />
        </Field>

        <Field
          label="Maximum delivery days"
          htmlFor={`${formId}-limit-days`}
          error={errors.limitDeliveryDays}
          required
        >
          <Input
            id={`${formId}-limit-days`}
            type="number"
            min={1}
            value={draft.limitDeliveryDays}
            onChange={(event) => set({ limitDeliveryDays: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.limitDeliveryDays)}
          />
        </Field>

        <Field label="Target delivery days" htmlFor={`${formId}-target-days`} error={errors.targetDeliveryDays} required>
          <Input
            id={`${formId}-target-days`}
            type="number"
            min={1}
            value={draft.targetDeliveryDays}
            onChange={(event) => set({ targetDeliveryDays: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.targetDeliveryDays)}
          />
        </Field>

        <Field
          label="Minimum revisions"
          htmlFor={`${formId}-limit-revisions`}
          error={errors.limitRevisions}
          required
        >
          <Input
            id={`${formId}-limit-revisions`}
            type="number"
            min={0}
            value={draft.limitRevisions}
            onChange={(event) => set({ limitRevisions: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.limitRevisions)}
          />
        </Field>

        <Field label="Target revisions" htmlFor={`${formId}-target-revisions`} error={errors.targetRevisions} required>
          <Input
            id={`${formId}-target-revisions`}
            type="number"
            min={0}
            value={draft.targetRevisions}
            onChange={(event) => set({ targetRevisions: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.targetRevisions)}
          />
        </Field>

        <Field
          label="Must-have deliverables"
          htmlFor={`${formId}-must-have`}
          hint="One requirement per line."
          className="sm:col-span-2"
        >
          <Textarea
            id={`${formId}-must-have`}
            rows={3}
            value={draft.mustHaveDeliverablesText}
            onChange={(event) => set({ mustHaveDeliverablesText: event.target.value })}
            disabled={disabled}
          />
        </Field>

        <Field
          label="Forbidden terms"
          htmlFor={`${formId}-forbidden`}
          hint="One term per line. The server blocks offers that violate these."
          className="sm:col-span-2"
        >
          <Textarea
            id={`${formId}-forbidden`}
            rows={3}
            value={draft.forbiddenTermsText}
            onChange={(event) => set({ forbiddenTermsText: event.target.value })}
            disabled={disabled}
          />
        </Field>

        <Field label="Maximum rounds" htmlFor={`${formId}-rounds`} error={errors.maxRounds} required>
          <Input
            id={`${formId}-rounds`}
            type="number"
            min={1}
            max={50}
            value={draft.maxRounds}
            onChange={(event) => set({ maxRounds: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.maxRounds)}
          />
        </Field>

        <Field label="Expires" htmlFor={`${formId}-expiry`} error={errors.expiresAt} required>
          <Input
            id={`${formId}-expiry`}
            type="datetime-local"
            value={draft.expiresAtLocal}
            onChange={(event) => set({ expiresAtLocal: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.expiresAt)}
          />
        </Field>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={submitting} disabled={disabled}>
          {submitLabel}
        </Button>
        <p className="text-xs leading-relaxed text-slate-500">
          UI validation improves the form only. The server remains the source of truth for mandate compliance.
        </p>
      </div>
    </form>
  );
}
