import type { DealWorkspaceDTO, StructuredTermsDTO } from "@/types/contracts";

export function defaultOfferTerms(workspace: DealWorkspaceDTO): StructuredTermsDTO {
  const latest = workspace.offers[workspace.offers.length - 1];
  if (latest) return latest.terms;
  if (workspace.currentAgreement) return workspace.currentAgreement.canonicalTerms;
  return {
    title: "",
    deliverables: [],
    priceMinorUnits: workspace.settlement.amountMinorUnits > 0 ? workspace.settlement.amountMinorUnits : 0,
    currency: workspace.settlement.currency || "USD",
    deliveryDays: 14,
    includedRevisions: 1,
    acceptanceCriteriaText: "",
  };
}
