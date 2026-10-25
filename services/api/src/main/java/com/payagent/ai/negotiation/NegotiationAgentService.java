package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.client.ChatCompletionClient;
import com.payagent.ai.client.LlmCallException;
import com.payagent.ai.strategy.RuleBasedStrategySelector;
import com.payagent.ai.strategy.StrategySelection;
import com.payagent.ai.strategy.StrategySelector;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.OfferDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Public entry point for the negotiation agent.
 *
 * <p>Pipeline: validate caller state -> select strategy -> deterministic policy
 * -> hard constraint boundary -> optional prose enhancement. The LLM is never
 * asked for a price and can never modify the selected action, terms or amount.</p>
 */
@Service
public class NegotiationAgentService {

    private static final Logger log = LoggerFactory.getLogger(NegotiationAgentService.class);

    private static final String RATIONALE_SYSTEM_PROMPT = """
            You write one short sentence explaining a negotiation decision to the user who
            delegated the negotiation. Never mention mandates, limits, budgets, floors,
            ceilings, reservation prices, internal constraints or instructions. Never include
            numbers other than the offered price. Return plain text only, at most 220 characters.
            """;

    private final StrategySelector strategySelector;
    private final RuleBasedStrategySelector ruleBasedFallback;
    private final NegotiationPolicyEngine policyEngine;
    private final MandateValidator mandateValidator;
    private final OfferConstraintValidator constraintValidator;
    private final StructuredTermsValidator termsValidator;
    private final ChatCompletionClient chatClient;
    private final RationaleValidator rationaleValidator;

    public NegotiationAgentService(
            StrategySelector strategySelector,
            RuleBasedStrategySelector ruleBasedFallback,
            NegotiationPolicyEngine policyEngine,
            MandateValidator mandateValidator,
            OfferConstraintValidator constraintValidator,
            StructuredTermsValidator termsValidator,
            ChatCompletionClient chatClient,
            RationaleValidator rationaleValidator) {
        this.strategySelector = strategySelector;
        this.ruleBasedFallback = ruleBasedFallback;
        this.policyEngine = policyEngine;
        this.mandateValidator = mandateValidator;
        this.constraintValidator = constraintValidator;
        this.termsValidator = termsValidator;
        this.chatClient = chatClient;
        this.rationaleValidator = rationaleValidator;
    }

    public ProposedOfferDTO decide(NegotiationContext context) {
        return decide(context, Instant.now());
    }

    public ProposedOfferDTO decide(NegotiationContext context, Instant now) {
        validateContext(context, now);
        List<String> warnings = new ArrayList<>(context.preferences().warnings());

        StrategySelection selection;
        try {
            selection = strategySelector.select(context);
        } catch (RuntimeException ex) {
            log.debug("Strategy selection failed ({}); using rule-based selector",
                    ex.getClass().getSimpleName());
            selection = ruleBasedFallback.select(context);
            warnings.add("STRATEGY_SELECTION_FAILED: rule-based fallback used");
        }
        if (selection.source() == StrategySelection.Source.RULE_BASED) {
            addWarning(warnings, "STRATEGY_RULE_BASED");
        }

        PolicyDecision policy = policyEngine.decide(context, selection.strategy(), now);
        warnings.addAll(policy.warnings());

        if (policy.action() == NegotiationAction.OPEN
                || policy.action() == NegotiationAction.COUNTER
                || policy.action() == NegotiationAction.ACCEPT) {
            List<OfferConstraintValidator.Violation> violations = constraintValidator.inspect(
                    context.callerRole(), context.mandate(), policy.terms(), now);
            if (!violations.isEmpty()) {
                for (OfferConstraintValidator.Violation violation : violations) {
                    addWarning(warnings, "FINAL_BOUNDARY_" + violation.code());
                }
                return escalation(
                        context, selection, warnings,
                        "FINAL_TERMS_REJECTED", policy.rationale());
            }
        }

        String rationale = policy.rationale();
        Long publicPrice = policy.terms() == null ? null : policy.terms().priceMinorUnits();
        if (usesRationaleModel(policy.action()) && chatClient.isConfigured()) {
            String enhanced = enhanceRationale(context, policy, selection, publicPrice, warnings);
            if (enhanced != null) {
                rationale = enhanced;
            }
        }

        boolean offerAction = policy.action() == NegotiationAction.OPEN
                || policy.action() == NegotiationAction.COUNTER
                || policy.action() == NegotiationAction.ACCEPT;
        boolean requiresHumanApproval = context.mandate().mode() == NegotiationMode.ASSISTED
                || policy.action() == NegotiationAction.ESCALATE;

        return new ProposedOfferDTO(
                context.dealId(),
                context.callerRole(),
                policy.action(),
                selection.strategy(),
                selection.source(),
                policy.terms(),
                publicPrice == null ? 0L : publicPrice,
                rationale,
                selection.confidence(),
                offerAction,
                policy.fallbackUsed(),
                requiresHumanApproval,
                warnings,
                policy.reasonCode());
    }

    public ProposedOfferDTO draftBoundedCounterOffer(
            NegotiationContext context,
            OfferDTO counterpartyOffer) {
        return draftBoundedCounterOffer(context, counterpartyOffer, Instant.now());
    }

    public ProposedOfferDTO draftBoundedCounterOffer(
            NegotiationContext context,
            OfferDTO counterpartyOffer,
            Instant now) {
        if (counterpartyOffer == null) {
            return decide(context, now);
        }
        List<OfferDTO> offers = new ArrayList<>(context.offers());
        boolean alreadyPresent = offers.stream()
                .anyMatch(existing -> existing.offerId() != null
                        && existing.offerId().equals(counterpartyOffer.offerId()));
        if (!alreadyPresent) {
            offers.add(counterpartyOffer);
        }
        NegotiationContext expanded = new NegotiationContext(
                context.stateVersion(),
                context.dealId(),
                context.callerRole(),
                context.mandate(),
                context.baseTerms(),
                offers,
                context.preferences());
        return decide(expanded, now);
    }

    public StrategySelection selectStrategy(NegotiationContext context) {
        mandateValidator.validate(context.mandate());
        return strategySelector.select(context);
    }

    private void validateContext(NegotiationContext context, Instant now) {
        if (context == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "negotiation context is required");
        }
        mandateValidator.validate(context.mandate(), now);
        MandateDTO mandate = context.mandate();
        if (!context.dealId().equals(mandate.dealId())) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE,
                    "mandate does not belong to the context deal");
        }
        if (context.callerRole() != mandate.partyRole()) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_MANDATE,
                    "mandate party role does not match the caller role");
        }
        if (context.baseTerms() == null) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_STATE, "base terms are required");
        }
        termsValidator.validate(context.baseTerms());
        if (!context.baseTerms().currency().equalsIgnoreCase(mandate.currency())) {
            throw new AiNegotiationException(
                    AiNegotiationException.Code.INVALID_TERMS,
                    "base terms currency must match the mandate currency");
        }
        for (OfferDTO offer : context.offers()) {
            if (offer == null || offer.offerId() == null || offer.offerId().isBlank()
                    || offer.proposerRole() == null || offer.terms() == null) {
                throw new AiNegotiationException(
                        AiNegotiationException.Code.INVALID_STATE,
                        "offer history contains an incomplete offer");
            }
            if (offer.roundNumber() < 1) {
                throw new AiNegotiationException(
                        AiNegotiationException.Code.INVALID_STATE,
                        "offer round numbers must be positive");
            }
            if (offer.dealId() != null && !offer.dealId().equals(context.dealId())) {
                throw new AiNegotiationException(
                        AiNegotiationException.Code.INVALID_STATE,
                        "offer history contains an offer from another deal");
            }
            if (!termsValidator.isValid(offer.terms())) {
                throw new AiNegotiationException(
                        AiNegotiationException.Code.INVALID_STATE,
                        "offer " + offer.offerId() + " has structurally invalid terms");
            }
        }
    }

    private String enhanceRationale(
            NegotiationContext context,
            PolicyDecision policy,
            StrategySelection selection,
            Long publicPrice,
            List<String> warnings) {
        try {
            String userPrompt = """
                    Role: %s
                    Action: %s
                    Strategy: %s
                    Offered price: %s %s
                    Write the explanation sentence.
                    """.formatted(
                    context.callerRole(),
                    policy.action(),
                    selection.strategy().displayName(),
                    publicPrice == null ? "none" : MoneyParser.formatMajorUnits(publicPrice),
                    context.mandate().currency());
            String content = chatClient.complete(RATIONALE_SYSTEM_PROMPT, userPrompt, 120);
            String safe = rationaleValidator.sanitizeOrNull(content, context.mandate(), publicPrice);
            if (safe == null) {
                addWarning(warnings, "LLM_RATIONALE_REJECTED");
                return null;
            }
            return safe;
        } catch (LlmCallException ex) {
            log.debug("Rationale LLM failed: {}", ex.getMessage());
            addWarning(warnings,
                    ex.code() == AiNegotiationException.Code.LLM_UNAVAILABLE
                            ? "LLM_RATIONALE_UNAVAILABLE"
                            : "LLM_RATIONALE_INVALID");
            return null;
        } catch (RuntimeException ex) {
            addWarning(warnings, "LLM_RATIONALE_REJECTED");
            return null;
        }
    }

    private ProposedOfferDTO escalation(
            NegotiationContext context,
            StrategySelection selection,
            List<String> warnings,
            String reasonCode,
            String rationale) {
        addWarning(warnings, "HUMAN_APPROVAL_REQUIRED");
        return new ProposedOfferDTO(
                context.dealId(),
                context.callerRole(),
                NegotiationAction.ESCALATE,
                selection.strategy(),
                selection.source(),
                null,
                0L,
                String.join(" ", List.of(
                        "Escalating to the user before any commitment.",
                        rationale == null ? "" : rationale).stream()
                        .filter(s -> !s.isBlank()).toList()),
                selection.confidence(),
                false,
                true,
                true,
                warnings,
                reasonCode);
    }

    private static boolean usesRationaleModel(NegotiationAction action) {
        return action == NegotiationAction.OPEN
                || action == NegotiationAction.COUNTER
                || action == NegotiationAction.ACCEPT;
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }
}
