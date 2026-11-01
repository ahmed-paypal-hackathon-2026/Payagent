import type { MandateDTO, NegotiationMode, PartyRole, StructuredTermsDTO } from "@/types/contracts";
import {
  fromDateTimeLocalValue,
  minorUnitsToDecimalInput,
  parseMoneyToMinorUnits,
  toDateTimeLocalValue,
} from "./format";
import type { MandateInput } from "./mock/types";

export interface TermsDraft {
  title: string;
  deliverablesText: string;
  priceInput: string;
  currency: string;
  deliveryDays: string;
  includedRevisions: string;
  acceptanceCriteriaText: string;
}

export interface MandateDraft {
  currency: string;
  limitPriceInput: string;
  targetPriceInput: string;
  limitDeliveryDays: string;
  targetDeliveryDays: string;
  limitRevisions: string;
  targetRevisions: string;
  mustHaveDeliverablesText: string;
  forbiddenTermsText: string;
  mode: NegotiationMode;
  maxRounds: string;
  expiresAtLocal: string;
}

export type FieldErrors = Record<string, string>;

export function draftFromTerms(terms: StructuredTermsDTO): TermsDraft {
  return {
    title: terms.title,
    deliverablesText: terms.deliverables.join("\n"),
    priceInput: minorUnitsToDecimalInput(terms.priceMinorUnits),
    currency: terms.currency,
    deliveryDays: String(terms.deliveryDays),
    includedRevisions: String(terms.includedRevisions),
    acceptanceCriteriaText: terms.acceptanceCriteriaText,
  };
}

export function termsFromDraft(draft: TermsDraft): { value: StructuredTermsDTO | null; errors: FieldErrors } {
  const errors: FieldErrors = {};
  if (!draft.title.trim()) errors.title = "A title is required.";
  const price = parseMoneyToMinorUnits(draft.priceInput, draft.currency);
  if (price === null || price <= 0) errors.price = "Enter a positive amount with up to 2 decimals.";
  const deliveryDays = Number(draft.deliveryDays);
  if (!draft.deliveryDays.trim() || !Number.isInteger(deliveryDays) || deliveryDays < 1) {
    errors.deliveryDays = "Enter at least 1 day.";
  }
  const includedRevisions = Number(draft.includedRevisions);
  if (!draft.includedRevisions.trim() || !Number.isInteger(includedRevisions) || includedRevisions < 0) {
    errors.includedRevisions = "Enter 0 or more revisions.";
  }
  const deliverables = draft.deliverablesText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  if (deliverables.length === 0) errors.deliverables = "List at least one deliverable.";
  if (!draft.acceptanceCriteriaText.trim()) {
    errors.acceptanceCriteria = "Acceptance criteria are required.";
  }
  if (!draft.currency.trim()) errors.currency = "Select a currency.";

  if (Object.keys(errors).length > 0 || price === null) return { value: null, errors };
  return {
    value: {
      title: draft.title.trim(),
      deliverables,
      priceMinorUnits: price,
      currency: draft.currency,
      deliveryDays,
      includedRevisions,
      acceptanceCriteriaText: draft.acceptanceCriteriaText.trim(),
    },
    errors,
  };
}

export function draftFromMandate(mandate: MandateDTO | null, role: PartyRole): MandateDraft {
  if (!mandate) {
    return {
      currency: "USD",
      limitPriceInput: "",
      targetPriceInput: "",
      limitDeliveryDays: "",
      targetDeliveryDays: "",
      limitRevisions: "",
      targetRevisions: "",
      mustHaveDeliverablesText: "",
      forbiddenTermsText: "",
      mode: role === "SELLER" ? "AUTONOMOUS_BOUNDED" : "ASSISTED",
      maxRounds: "5",
      expiresAtLocal: "",
    };
  }
  return {
    currency: mandate.currency,
    limitPriceInput: minorUnitsToDecimalInput(mandate.limitPriceMinorUnits),
    targetPriceInput: minorUnitsToDecimalInput(mandate.targetPriceMinorUnits),
    limitDeliveryDays: String(mandate.limitDeliveryDays),
    targetDeliveryDays: String(mandate.targetDeliveryDays),
    limitRevisions: String(mandate.limitRevisions),
    targetRevisions: String(mandate.targetRevisions),
    mustHaveDeliverablesText: mandate.mustHaveDeliverables.join("\n"),
    forbiddenTermsText: mandate.forbiddenTerms.join("\n"),
    mode: mandate.mode,
    maxRounds: String(mandate.maxRounds),
    expiresAtLocal: toDateTimeLocalValue(mandate.expiresAt),
  };
}

export function mandateFromDraft(
  draft: MandateDraft,
  role: PartyRole,
): { value: MandateInput | null; errors: FieldErrors } {
  const errors: FieldErrors = {};
  const limitPrice = parseMoneyToMinorUnits(draft.limitPriceInput, draft.currency);
  const targetPrice = parseMoneyToMinorUnits(draft.targetPriceInput, draft.currency);
  if (limitPrice === null || limitPrice <= 0) errors.limitPrice = "Enter a positive limit amount.";
  if (targetPrice === null || targetPrice <= 0) errors.targetPrice = "Enter a positive target amount.";
  if (limitPrice !== null && targetPrice !== null) {
    if (role === "BUYER" && targetPrice > limitPrice) {
      errors.targetPrice = "A buyer target cannot exceed the limit.";
    }
    if (role === "SELLER" && targetPrice < limitPrice) {
      errors.targetPrice = "A seller target cannot be below the limit.";
    }
  }

  const limitDeliveryDays = Number(draft.limitDeliveryDays);
  const targetDeliveryDays = Number(draft.targetDeliveryDays);
  if (!draft.limitDeliveryDays.trim() || !Number.isInteger(limitDeliveryDays) || limitDeliveryDays < 1) {
    errors.limitDeliveryDays = "Enter at least 1 day.";
  }
  if (!draft.targetDeliveryDays.trim() || !Number.isInteger(targetDeliveryDays) || targetDeliveryDays < 1) {
    errors.targetDeliveryDays = "Enter at least 1 day.";
  }

  const limitRevisions = Number(draft.limitRevisions);
  const targetRevisions = Number(draft.targetRevisions);
  if (!draft.limitRevisions.trim() || !Number.isInteger(limitRevisions) || limitRevisions < 0) {
    errors.limitRevisions = "Enter 0 or more revisions.";
  }
  if (!draft.targetRevisions.trim() || !Number.isInteger(targetRevisions) || targetRevisions < 0) {
    errors.targetRevisions = "Enter 0 or more revisions.";
  }

  const maxRounds = Number(draft.maxRounds);
  if (!draft.maxRounds.trim() || !Number.isInteger(maxRounds) || maxRounds < 1 || maxRounds > 50) {
    errors.maxRounds = "Enter between 1 and 50 rounds.";
  }

  const expiresAt = fromDateTimeLocalValue(draft.expiresAtLocal);
  if (!expiresAt) errors.expiresAt = "Choose a mandate expiry.";
  else if (new Date(expiresAt).getTime() <= Date.now()) errors.expiresAt = "Expiry must be in the future.";

  const mustHaveDeliverables = draft.mustHaveDeliverablesText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  const forbiddenTerms = draft.forbiddenTermsText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  if (Object.keys(errors).length > 0 || limitPrice === null || targetPrice === null || !expiresAt) {
    return { value: null, errors };
  }

  return {
    value: {
      currency: draft.currency,
      limitPriceMinorUnits: limitPrice,
      targetPriceMinorUnits: targetPrice,
      limitDeliveryDays,
      targetDeliveryDays,
      limitRevisions,
      targetRevisions,
      mustHaveDeliverables,
      forbiddenTerms,
      mode: draft.mode,
      maxRounds,
      expiresAt,
    },
    errors,
  };
}

export function nextFortnightLocal(): string {
  const date = new Date();
  date.setDate(date.getDate() + 14);
  return toDateTimeLocalValue(date.toISOString());
}
