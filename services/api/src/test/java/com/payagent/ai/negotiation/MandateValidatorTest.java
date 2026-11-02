package com.payagent.ai.negotiation;

import com.payagent.ai.AiNegotiationException;
import com.payagent.ai.AiTestFixtures;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MandateValidatorTest {

    private final MandateValidator validator = new MandateValidator();

    @Test
    void acceptsValidBuyerAndSellerMandates() {
        assertDoesNotThrow(() -> validator.validate(AiTestFixtures.buyerMandate(), AiTestFixtures.NOW));
        assertDoesNotThrow(() -> validator.validate(AiTestFixtures.sellerMandate(), AiTestFixtures.NOW));
    }

    @Test
    void enforcesBuyerCeilingAndMinimums() {
        assertInvalid(new MandateDTO(
                "m", "d", PartyRole.BUYER, 1, "USD",
                100_000L, 110_000L, 30, 31, 2, 1,
                List.of(), List.of(), com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                5, AiTestFixtures.FUTURE));
        assertInvalid(new MandateDTO(
                "m", "d", PartyRole.BUYER, 1, "USD",
                100_000L, 90_000L, 30, 31, 2, 2,
                List.of(), List.of(), com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                5, AiTestFixtures.FUTURE));
        assertInvalid(new MandateDTO(
                "m", "d", PartyRole.BUYER, 1, "USD",
                100_000L, 90_000L, 30, 20, 2, 1,
                List.of(), List.of(), com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                5, AiTestFixtures.FUTURE));
        assertDoesNotThrow(() -> validator.validate(
                AiTestFixtures.sellerMandate(), AiTestFixtures.NOW));
    }

    @Test
    void enforcesSellerFloorAndMaximums() {
        assertInvalid(new MandateDTO(
                "m", "d", PartyRole.SELLER, 1, "USD",
                70_000L, 60_000L, 14, 21, 2, 2,
                List.of(), List.of(), com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                5, AiTestFixtures.FUTURE));
        assertInvalid(new MandateDTO(
                "m", "d", PartyRole.SELLER, 1, "USD",
                70_000L, 90_000L, 14, 13, 2, 2,
                List.of(), List.of(), com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                5, AiTestFixtures.FUTURE));
        assertInvalid(new MandateDTO(
                "m", "d", PartyRole.SELLER, 1, "USD",
                70_000L, 90_000L, 14, 21, 2, 3,
                List.of(), List.of(), com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                5, AiTestFixtures.FUTURE));
    }

    @Test
    void rejectsExpiredMandate() {
        MandateDTO expired = AiTestFixtures.withExpiry(
                AiTestFixtures.buyerMandate(), "2029-12-30T00:00:00Z");
        assertCode(AiNegotiationException.Code.MANDATE_EXPIRED,
                () -> validator.validate(expired, AiTestFixtures.NOW));
    }

    @Test
    void rejectsMalformedMandate() {
        assertCode(AiNegotiationException.Code.INVALID_MANDATE,
                () -> validator.validate(null, AiTestFixtures.NOW));

        MandateDTO invalid = new MandateDTO(
                "", "deal", PartyRole.BUYER, 0, "US", -1L, -1L,
                0, 0, -1, -1, List.of(), List.of(),
                com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                0, "not-an-instant");
        assertCode(AiNegotiationException.Code.INVALID_MANDATE,
                () -> validator.validate(invalid, AiTestFixtures.NOW));
    }

    @Test
    void rejectsDuplicateMandateLists() {
        MandateDTO invalid = new MandateDTO(
                "m", "d", PartyRole.BUYER, 1, "USD", 100_000L, 90_000L,
                30, 21, 2, 2,
                List.of("source code", " Source Code "), List.of("crypto"),
                com.payagent.common.contracts.NegotiationMode.AUTONOMOUS_BOUNDED,
                5, AiTestFixtures.FUTURE);
        assertCode(AiNegotiationException.Code.INVALID_MANDATE,
                () -> validator.validate(invalid, AiTestFixtures.NOW));
    }

    private void assertInvalid(MandateDTO mandate) {
        assertCode(AiNegotiationException.Code.INVALID_MANDATE,
                () -> validator.validate(mandate, AiTestFixtures.NOW));
    }

    private static void assertCode(
            AiNegotiationException.Code expected,
            org.junit.jupiter.api.function.Executable executable) {
        AiNegotiationException error = assertThrows(AiNegotiationException.class, executable);
        assertEquals(expected, error.code());
    }
}
