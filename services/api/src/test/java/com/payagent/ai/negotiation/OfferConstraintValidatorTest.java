package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.AiTestFixtures;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferConstraintValidatorTest {

    private final OfferConstraintValidator validator = new OfferConstraintValidator();

    @Test
    void acceptsBuyerTermsAtTheCeiling() {
        assertTrue(validator.inspect(
                PartyRole.BUYER,
                AiTestFixtures.buyerMandate(),
                AiTestFixtures.terms(100_000L),
                AiTestFixtures.NOW).isEmpty());
    }

    @Test
    void blocksBuyerTermsAboveCeiling() {
        List<OfferConstraintValidator.Violation> violations = validator.inspect(
                PartyRole.BUYER,
                AiTestFixtures.buyerMandate(),
                AiTestFixtures.terms(100_001L),
                AiTestFixtures.NOW);
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.BUYER_PRICE_LIMIT_EXCEEDED));
    }

    @Test
    void blocksBuyerDeliveryAndRevisionViolations() {
        StructuredTermsDTO terms = AiTestFixtures.terms(
                90_000L, 31, 1, "Website build",
                List.of("source code", "documentation"));
        List<OfferConstraintValidator.Violation> violations = validator.inspect(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(), terms, AiTestFixtures.NOW);
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.BUYER_DELIVERY_LIMIT_EXCEEDED));
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.BUYER_REVISIONS_MINIMUM_VIOLATED));
    }

    @Test
    void blocksSellerBelowFloorAndExcessRevisions() {
        StructuredTermsDTO terms = AiTestFixtures.terms(
                69_999L, 13, 3, "Website build",
                List.of("source code", "documentation"));
        List<OfferConstraintValidator.Violation> violations = validator.inspect(
                PartyRole.SELLER, AiTestFixtures.sellerMandate(), terms, AiTestFixtures.NOW);
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.SELLER_PRICE_FLOOR_VIOLATED));
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.SELLER_DELIVERY_MINIMUM_VIOLATED));
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.SELLER_REVISIONS_MAXIMUM_VIOLATED));
    }

    @Test
    void acceptsSellerAtFloor() {
        assertTrue(validator.inspect(
                PartyRole.SELLER,
                AiTestFixtures.sellerMandate(),
                AiTestFixtures.terms(70_000L),
                AiTestFixtures.NOW).isEmpty());
    }

    @Test
    void blocksMissingRequiredAndForbiddenTerms() {
        StructuredTermsDTO terms = AiTestFixtures.terms(
                90_000L, 21, 2, "Website with crypto payment",
                List.of("documentation"));
        List<OfferConstraintValidator.Violation> violations = validator.inspect(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(), terms, AiTestFixtures.NOW);
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.REQUIRED_DELIVERABLE_MISSING));
        assertTrue(codes(violations).contains(
                OfferConstraintValidator.Code.FORBIDDEN_TERM_PRESENT));
    }

    @Test
    void blocksCurrencyMismatchAndExpiredMandate() {
        List<OfferConstraintValidator.Violation> currency = validator.inspect(
                PartyRole.BUYER,
                AiTestFixtures.buyerMandate(),
                AiTestFixtures.terms(90_000L, "EUR"),
                AiTestFixtures.NOW);
        assertTrue(codes(currency).contains(OfferConstraintValidator.Code.CURRENCY_MISMATCH));

        List<OfferConstraintValidator.Violation> expired = validator.inspect(
                PartyRole.BUYER,
                AiTestFixtures.withExpiry(
                        AiTestFixtures.buyerMandate(), "2029-12-30T00:00:00Z"),
                AiTestFixtures.terms(90_000L),
                AiTestFixtures.NOW);
        assertTrue(codes(expired).contains(OfferConstraintValidator.Code.MANDATE_EXPIRED));
    }

    @Test
    void requireCompliantThrowsForUnsafeTerms() {
        assertFalse(validator.isCompliant(
                PartyRole.BUYER, AiTestFixtures.buyerMandate(), AiTestFixtures.terms(200_000L)));
        AiNegotiationException error = assertThrows(AiNegotiationException.class,
                () -> validator.requireCompliant(
                        PartyRole.BUYER,
                        AiTestFixtures.buyerMandate(),
                        AiTestFixtures.terms(200_000L)));
        assertEquals(AiNegotiationException.Code.INVALID_TERMS, error.code());
    }

    private static List<OfferConstraintValidator.Code> codes(
            List<OfferConstraintValidator.Violation> violations) {
        return violations.stream().map(OfferConstraintValidator.Violation::code).toList();
    }
}
