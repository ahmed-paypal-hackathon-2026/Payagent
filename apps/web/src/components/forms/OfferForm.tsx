"use client";

import { useId, useState } from "react";
import type { MandateDTO, StructuredTermsDTO } from "@/types/contracts";
import { draftFromTerms, termsFromDraft, type FieldErrors, type TermsDraft } from "@/lib/forms";
import { formatMinorUnits, minorUnitsToDecimalInput } from "@/lib/format";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { Field, Textarea } from "@/components/ui/Field";
import { TermsFields } from "./TermsFields";

export function OfferForm({
  initialTerms,
  mandate,
  onSubmit,
  disabled = false,
  disabledReason,
  submitLabel = "Submit offer",
  onGenerateAgent,
  agentLoading = false,
}: {
  initialTerms: StructuredTermsDTO;
  mandate: MandateDTO | null;
  onSubmit: (terms: StructuredTermsDTO, message: string) => Promise<void>;
  disabled?: boolean;
  disabledReason?: string;
  submitLabel?: string;
  onGenerateAgent?: () => Promise<void>;
  agentLoading?: boolean;
}) {
  const formId = useId().replace(/:/g, "");
  const [draft, setDraft] = useState<TermsDraft>(() => draftFromTerms(initialTerms));
  const [message, setMessage] = useState("");
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function useTargetTerms() {
    if (!mandate) return;
    setDraft((current) => ({
      ...current,
      priceInput: minorUnitsToDecimalInput(mandate.targetPriceMinorUnits),
      deliveryDays: String(mandate.targetDeliveryDays),
      includedRevisions: String(mandate.targetRevisions),
    }));
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const parsed = termsFromDraft(draft);
    setErrors(parsed.errors);
    if (!parsed.value) {
      setFormError("Review the highlighted offer fields.");
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit(parsed.value, message);
      setMessage("");
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "The offer could not be submitted.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-5" noValidate>
      {disabled && disabledReason ? <Alert tone="warning">{disabledReason}</Alert> : null}
      {formError ? <Alert tone="error">{formError}</Alert> : null}

      {mandate ? (
        <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-xs leading-relaxed text-slate-600">
          <p className="font-semibold text-slate-800">Your private guardrails</p>
          <p className="mt-1">
            {mandate.partyRole === "BUYER" ? "Maximum" : "Minimum"} price{" "}
            <span className="font-semibold text-slate-900">
              {formatMinorUnits(mandate.limitPriceMinorUnits, mandate.currency)}
            </span>{" "}
            · Target{" "}
            <span className="font-semibold text-slate-900">
              {formatMinorUnits(mandate.targetPriceMinorUnits, mandate.currency)}
            </span>{" "}
            · Delivery up to {mandate.limitDeliveryDays} days · Minimum {mandate.limitRevisions} revisions
          </p>
          <button
            type="button"
            onClick={useTargetTerms}
            disabled={disabled}
            className="mt-2 rounded-md border border-slate-300 bg-white px-2.5 py-1 text-[11px] font-semibold text-slate-700 hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700 disabled:cursor-not-allowed disabled:text-slate-400"
          >
            Prefill with my target terms
          </button>
        </div>
      ) : (
        <Alert tone="warning" title="Lock a mandate first">
          The API rejects offers from a party without an active mandate.
        </Alert>
      )}

      <TermsFields
        draft={draft}
        onChange={setDraft}
        errors={errors}
        idPrefix={formId}
        disabled={disabled}
      />

      <Field
        label="Rationale"
        htmlFor={`${formId}-message`}
        hint="Shared with the other party. Do not include private mandate details."
      >
        <Textarea
          id={`${formId}-message`}
          rows={3}
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          disabled={disabled}
          maxLength={1000}
          placeholder="Explain the scope or timeline behind this round."
        />
      </Field>

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={submitting} disabled={disabled || !mandate}>
          {submitLabel}
        </Button>
        {onGenerateAgent ? (
          <Button
            variant="secondary"
            onClick={onGenerateAgent}
            loading={agentLoading}
            disabled={disabled || !mandate || submitting}
          >
            Generate demo agent counteroffer
          </Button>
        ) : null}
        <p className="text-xs leading-relaxed text-slate-500">
          UI validation is advisory. In live mode the server re-checks every term against your mandate.
        </p>
      </div>
    </form>
  );
}
