package com.payagent.releasegates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.agreements.*;
import com.payagent.auth.*;
import com.payagent.common.contracts.*;
import com.payagent.deals.*;
import com.payagent.settlement.SettlementReconciliationService;
import com.payagent.settlement.persistence.PayPalEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring security chain, services and H2 persistence. No live PayPal/model calls. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationReleaseGateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DealRepository deals;
    @Autowired AgreementRepository agreements;
    @Autowired AgreementService agreementService;
    @Autowired JwtTokenProvider tokens;
    @Autowired PayPalEventRepository events;
    @Autowired SettlementReconciliationService reconciliation;

    private User user() {
        UUID id = UUID.randomUUID();
        return users.save(new User(id, id + "@example.test", "unused-test-password-hash", OffsetDateTime.now()));
    }

    @Test
    void tst02_persistedAgreementInvalidatesBuyerConsentAfterOneCentEdit() {
        User buyer = user();
        User seller = user();
        Deal deal = deals.save(new Deal(UUID.randomUUID(), buyer.getId(), seller.getId(), DealStatus.ACTIVE_NEGOTIATION));
        var v1 = agreementService.proposeAgreement(deal.getId(), terms(98000));
        agreementService.acceptAgreement(deal.getId(), 1, v1.termsHashSha256(), "BUYER");
        assertNotNull(agreements.findByDealIdAndVersion(deal.getId(), 1).orElseThrow().getBuyerAcceptedAt());
        var v2 = agreementService.proposeNewVersion(deal.getId(), terms(98001));
        agreements.flush();
        assertEquals(2, v2.version());
        assertNotEquals(v1.termsHashSha256(), v2.termsHashSha256());
        assertNull(agreements.findByDealIdAndVersion(deal.getId(), 1).orElseThrow().getBuyerAcceptedAt());
        assertNull(agreements.findByDealIdAndVersion(deal.getId(), 2).orElseThrow().getSellerAcceptedAt());
        assertThrows(AgreementConflictException.class,
                () -> agreementService.acceptAgreement(deal.getId(), 1, v1.termsHashSha256(), "SELLER"));
    }

    @Test
    void tst07_realSecurityChainRejectsUnsignedWebhook() throws Exception {
        mvc.perform(post("/api/v1/paypal/webhooks").contentType("application/json")
                .content("{\"id\":\"WH-UNSIGNED\",\"event_type\":\"PAYMENT.CAPTURE.COMPLETED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"));
        assertFalse(events.existsByEventId("WH-UNSIGNED"));
    }

    @Test
    void tst08_databaseStoresDuplicateEventOnlyOnce() throws Exception {
        String id = "WH-" + UUID.randomUUID();
        String payload = "{\"id\":\"" + id + "\",\"event_type\":\"RELEASE.TEST.UNKNOWN\",\"resource\":{\"id\":\"synthetic\"}}";
        long before = events.count();
        var node = new ObjectMapper().readTree(payload).get("resource");
        assertTrue(reconciliation.processWebhookEvent(id, "RELEASE.TEST.UNKNOWN", "synthetic", node, payload));
        events.flush();
        assertFalse(reconciliation.processWebhookEvent(id, "RELEASE.TEST.UNKNOWN", "synthetic", node, payload));
        assertEquals(before + 1, events.count());
    }

    @Test
    void tst10_realJwtOutsiderCannotReadAnotherDealsSettlement() throws Exception {
        User buyer = user();
        User seller = user();
        User outsider = user();
        Deal deal = deals.save(new Deal(UUID.randomUUID(), buyer.getId(), seller.getId(), DealStatus.IN_PROGRESS));
        mvc.perform(get("/api/v1/deals/" + deal.getId() + "/settlement")
                .header("Authorization", "Bearer " + tokens.createToken(outsider.getId(), outsider.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void tst14_expiredJwtReturns401OnProtectedEndpoint() throws Exception {
        User buyer = user();
        Deal deal = deals.save(new Deal(UUID.randomUUID(), buyer.getId(), null, DealStatus.IN_PROGRESS));
        JwtTokenProvider expired = new JwtTokenProvider("test-jwt-secret-minimum-32-characters-long", -1);
        mvc.perform(get("/api/v1/deals/" + deal.getId() + "/settlement")
                .header("Authorization", "Bearer " + expired.createToken(buyer.getId(), buyer.getEmail())))
                .andExpect(status().isUnauthorized());
    }

    private StructuredTermsDTO terms(long cents) {
        return new StructuredTermsDTO("Release integration", List.of("source code"), cents, "USD", 7, 2, "Build succeeds");
    }
}
