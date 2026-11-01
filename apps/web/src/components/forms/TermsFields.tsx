import type { FieldErrors, TermsDraft } from "@/lib/forms";
import { Field, Input, Select, Textarea } from "@/components/ui/Field";

export function TermsFields({
  draft,
  onChange,
  errors,
  idPrefix,
  disabled = false,
  showCurrency = true,
}: {
  draft: TermsDraft;
  onChange: (draft: TermsDraft) => void;
  errors: FieldErrors;
  idPrefix: string;
  disabled?: boolean;
  showCurrency?: boolean;
}) {
  const set = (patch: Partial<TermsDraft>) => onChange({ ...draft, ...patch });

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field
        label="Title"
        htmlFor={`${idPrefix}-title`}
        error={errors.title}
        required
        className="sm:col-span-2"
      >
        <Input
          id={`${idPrefix}-title`}
          value={draft.title}
          onChange={(event) => set({ title: event.target.value })}
          disabled={disabled}
          aria-invalid={Boolean(errors.title)}
        />
      </Field>

      <Field
        label="Deliverables"
        htmlFor={`${idPrefix}-deliverables`}
        hint="One deliverable per line. These are shown to the other party in offers."
        error={errors.deliverables}
        required
        className="sm:col-span-2"
      >
        <Textarea
          id={`${idPrefix}-deliverables`}
          value={draft.deliverablesText}
          onChange={(event) => set({ deliverablesText: event.target.value })}
          rows={4}
          disabled={disabled}
          aria-invalid={Boolean(errors.deliverables)}
        />
      </Field>

      <Field
        label="Price"
        htmlFor={`${idPrefix}-price`}
        hint="Enter a decimal amount. The API receives integer minor units."
        error={errors.price}
        required
      >
        <div className="flex gap-2">
          <Input
            id={`${idPrefix}-price`}
            inputMode="decimal"
            value={draft.priceInput}
            onChange={(event) => set({ priceInput: event.target.value })}
            disabled={disabled}
            aria-invalid={Boolean(errors.price)}
            placeholder="900.00"
          />
          {showCurrency ? (
            <Select
              aria-label="Currency"
              value={draft.currency}
              onChange={(event) => set({ currency: event.target.value })}
              disabled={disabled}
              className="w-24 shrink-0"
            >
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
            </Select>
          ) : null}
        </div>
      </Field>

      <Field label="Delivery days" htmlFor={`${idPrefix}-days`} error={errors.deliveryDays} required>
        <Input
          id={`${idPrefix}-days`}
          type="number"
          min={1}
          step={1}
          value={draft.deliveryDays}
          onChange={(event) => set({ deliveryDays: event.target.value })}
          disabled={disabled}
          aria-invalid={Boolean(errors.deliveryDays)}
        />
      </Field>

      <Field
        label="Included revisions"
        htmlFor={`${idPrefix}-revisions`}
        error={errors.includedRevisions}
        required
      >
        <Input
          id={`${idPrefix}-revisions`}
          type="number"
          min={0}
          step={1}
          value={draft.includedRevisions}
          onChange={(event) => set({ includedRevisions: event.target.value })}
          disabled={disabled}
          aria-invalid={Boolean(errors.includedRevisions)}
        />
      </Field>

      <Field
        label="Acceptance criteria"
        htmlFor={`${idPrefix}-criteria`}
        hint="What must be true for the buyer to accept the deliverables?"
        error={errors.acceptanceCriteria}
        required
        className="sm:col-span-2"
      >
        <Textarea
          id={`${idPrefix}-criteria`}
          value={draft.acceptanceCriteriaText}
          onChange={(event) => set({ acceptanceCriteriaText: event.target.value })}
          rows={3}
          disabled={disabled}
          aria-invalid={Boolean(errors.acceptanceCriteria)}
        />
      </Field>
    </div>
  );
}
