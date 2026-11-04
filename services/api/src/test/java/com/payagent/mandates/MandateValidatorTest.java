package com.payagent.mandates;

import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MandateValidatorTest {

    private final MandateValidator validator = new MandateValidator();

    private MandateService.MandateCreateRequest buyerRequest() {
        return new MandateService.MandateCreateRequest(
                "USD", 120_000L, 90_000L, 20, 15, 2, 4,
                List.of("core"), List.of("custom-tooling"),
                NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1));
    }

    private MandateService.MandateCreateRequest sellerRequest() {
        return new MandateService.MandateCreateRequest(
                "USD", 90_000L, 120_000L, 10, 15, 3, 1,
                List.of("core"), List.of(),
                NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1));
    }

    @Test
    void acceptsValidBuyerAndSellerMandates() {
        assertDoesNotThrow(() -> validator.validate(buyerRequest(), PartyRole.BUYER));
        assertDoesNotThrow(() -> validator.validate(sellerRequest(), PartyRole.SELLER));
    }

    @Test
    void rejectsBuyerTargetPriceAboveLimit() {
        MandateService.MandateCreateRequest req = buyerRequest();
        MandateService.MandateCreateRequest invalid = new MandateService.MandateCreateRequest(
                req.currency(), 100_000L, 120_000L, req.limitDeliveryDays(), req.targetDeliveryDays(),
                req.limitRevisions(), req.targetRevisions(), req.mustHaveDeliverables(), req.forbiddenTerms(),
                req.mode(), req.maxRounds(), req.expiresAt());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalid, PartyRole.BUYER));
        assertTrue(ex.getMessage().contains("Target price"));
    }

    @Test
    void rejectsSellerTargetPriceBelowLimit() {
        MandateService.MandateCreateRequest req = sellerRequest();
        MandateService.MandateCreateRequest invalid = new MandateService.MandateCreateRequest(
                req.currency(), 120_000L, 90_000L, req.limitDeliveryDays(), req.targetDeliveryDays(),
                req.limitRevisions(), req.targetRevisions(), req.mustHaveDeliverables(), req.forbiddenTerms(),
                req.mode(), req.maxRounds(), req.expiresAt());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalid, PartyRole.SELLER));
        assertTrue(ex.getMessage().contains("Target price"));
    }

    @Test
    void rejectsInvalidCurrency() {
        MandateService.MandateCreateRequest req = buyerRequest();
        MandateService.MandateCreateRequest invalid = new MandateService.MandateCreateRequest(
                "US1", req.limitPriceMinorUnits(), req.targetPriceMinorUnits(), req.limitDeliveryDays(),
                req.targetDeliveryDays(), req.limitRevisions(), req.targetRevisions(),
                req.mustHaveDeliverables(), req.forbiddenTerms(), req.mode(), req.maxRounds(), req.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid, PartyRole.BUYER));
    }

    @Test
    void rejectsBuyerTargetDeliveryAboveLimit() {
        MandateService.MandateCreateRequest req = buyerRequest();
        MandateService.MandateCreateRequest invalid = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.limitDeliveryDays() + 1,
                req.limitRevisions(), req.targetRevisions(), req.mustHaveDeliverables(), req.forbiddenTerms(),
                req.mode(), req.maxRounds(), req.expiresAt());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalid, PartyRole.BUYER));
        assertTrue(ex.getMessage().contains("delivery days"));
    }

    @Test
    void rejectsSellerTargetRevisionsAboveLimit() {
        MandateService.MandateCreateRequest req = sellerRequest();
        MandateService.MandateCreateRequest invalid = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.targetDeliveryDays(),
                req.limitRevisions(), req.limitRevisions() + 1,
                req.mustHaveDeliverables(), req.forbiddenTerms(), req.mode(), req.maxRounds(), req.expiresAt());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalid, PartyRole.SELLER));
        assertTrue(ex.getMessage().contains("revisions"));
    }

    @Test
    void rejectsInvalidMaxRounds() {
        MandateService.MandateCreateRequest req = buyerRequest();
        MandateService.MandateCreateRequest zeroRounds = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.targetDeliveryDays(), req.limitRevisions(), req.targetRevisions(),
                req.mustHaveDeliverables(), req.forbiddenTerms(), req.mode(), 0, req.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(zeroRounds, PartyRole.BUYER));

        MandateService.MandateCreateRequest tooManyRounds = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.targetDeliveryDays(), req.limitRevisions(), req.targetRevisions(),
                req.mustHaveDeliverables(), req.forbiddenTerms(), req.mode(), MandateValidator.MAX_ROUNDS_LIMIT + 1,
                req.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooManyRounds, PartyRole.BUYER));
    }

    @Test
    void rejectsPastExpiry() {
        MandateService.MandateCreateRequest req = buyerRequest();
        MandateService.MandateCreateRequest invalid = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.targetDeliveryDays(), req.limitRevisions(), req.targetRevisions(),
                req.mustHaveDeliverables(), req.forbiddenTerms(), req.mode(), req.maxRounds(),
                OffsetDateTime.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(invalid, PartyRole.BUYER));
    }

    @Test
    void rejectsDuplicateAndBlankListEntries() {
        MandateService.MandateCreateRequest req = buyerRequest();
        MandateService.MandateCreateRequest duplicates = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.targetDeliveryDays(), req.limitRevisions(), req.targetRevisions(),
                List.of("core", "Core"), req.forbiddenTerms(), req.mode(), req.maxRounds(), req.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(duplicates, PartyRole.BUYER));

        MandateService.MandateCreateRequest blank = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.targetDeliveryDays(), req.limitRevisions(), req.targetRevisions(),
                List.of("core", " "), req.forbiddenTerms(), req.mode(), req.maxRounds(), req.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(blank, PartyRole.BUYER));
    }

    @Test
    void rejectsMissingModeAndNullLists() {
        MandateService.MandateCreateRequest req = buyerRequest();
        MandateService.MandateCreateRequest noMode = new MandateService.MandateCreateRequest(
                req.currency(), req.limitPriceMinorUnits(), req.targetPriceMinorUnits(),
                req.limitDeliveryDays(), req.targetDeliveryDays(), req.limitRevisions(), req.targetRevisions(),
                req.mustHaveDeliverables(), req.forbiddenTerms(), null, req.maxRounds(), req.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(noMode, PartyRole.BUYER));
    }
}
