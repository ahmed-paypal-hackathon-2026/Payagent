package com.payagent.releasegates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.agreements.Rfc8785Canonicalizer;
import com.payagent.agreements.Sha256Hasher;
import com.payagent.common.contracts.*;
import jakarta.validation.Validation;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FixtureReleaseGateTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root = Path.of("../../shared/fixtures");

    private <T> T read(String file, Class<T> type) throws Exception {
        T value = mapper.readValue(root.resolve(file).toFile(), type);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(value).isEmpty(), "Invalid DTO fixture: " + file);
        }
        return value;
    }

    @Test
    void tenFixturesIncludeValidDtosAndConsistentSettlementAmounts() throws Exception {
        try (var files = Files.list(root)) {
            assertEquals(10, files.filter(p -> p.toString().endsWith(".json")).count());
        }
        read("01-buyer-intake.json", StructuredTermsDTO.class);
        read("02-seller-counteroffer.json", OfferDTO.class);
        read("03-over-budget-offer.json", OfferDTO.class);
        var agreement = read("06-accepted-agreement-snapshot.json", AgreementSnapshotDTO.class);
        var settlement = read("07-settlement-invoice-and-authorization.json", SettlementStateDTO.class);
        var evidence = read("09-deliverable-evidence.json", DeliverableEvidenceDTO.class);
        var disputed = read("10-active-dispute-workspace.json", DealWorkspaceDTO.class);
        assertEquals(agreement.dealId(), settlement.dealId());
        assertEquals(agreement.dealId(), evidence.dealId());
        assertEquals(agreement.version(), settlement.agreementVersion());
        assertEquals(agreement.canonicalTerms().priceMinorUnits(), settlement.amountMinorUnits());
        assertEquals(agreement.canonicalTerms().currency(), settlement.currency());
        assertEquals(DealStatus.DISPUTED, disputed.status());
        assertEquals(DisputeStatus.OPEN, disputed.disputeStatus());
        assertEquals(PaymentLifecycleStatus.AUTHORIZED, disputed.settlement().paymentStatus());
        assertNull(disputed.settlement().paypalCaptureId());
        assertNotNull(agreement.buyerAcceptedAt());
        assertNotNull(agreement.sellerAcceptedAt());
        assertEquals(Sha256Hasher.hash(Rfc8785Canonicalizer.canonicalBytes(agreement.canonicalTerms())), agreement.termsHashSha256());
        var replay = mapper.readTree(root.resolve("08-duplicate-webhook-event.json").toFile());
        assertEquals(replay.get("eventId"), replay.at("/payload/id"));
        assertEquals(replay.get("eventType"), replay.at("/payload/event_type"));
        assertEquals(replay.get("resourceId"), replay.at("/payload/resource/id"));
    }
}
