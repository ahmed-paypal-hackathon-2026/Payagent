package com.payagent;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealService;
import com.payagent.mandates.MandateService;
import com.payagent.mandates.UtilityCalculator;
import com.payagent.agreements.AgreementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A7 — Integration & Regression.
 *
 * End-to-end mandate → utility → agreement lifecycle test covering:
 * 1. Deal creation with buyer/seller participants
 * 2. Mandate creation with validation (server-derived role, privacy)
 * 3. Utility calculation for both parties
 * 4. Agreement proposal with RFC-8785 canonical hash
 * 5. Dual acceptance → deal transitions to AGREED
 * 6. Stale version/hash rejection
 * 7. No mandate data leakage to counterparty
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PhaseA7IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DealService dealService;
    @Autowired private MandateService mandateService;
    @Autowired private AgreementService agreementService;

    private static final List<String> DELIVERED = List.of("figma", "source", "notes");

    private User makeUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "hash", OffsetDateTime.now()));
    }

    private UserDetails userDetails(User u) {
        return new org.springframework.security.core.userdetails.User(
                u.getEmail(), "password", List.of());
    }

    @Test
    void fullMandateToAgreementLifecycle() throws Exception {
        // 1. Create deal with buyer and seller
        User buyer = makeUser("e2e-buyer@test.com");
        User seller = makeUser("e2e-seller@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), seller.getId());
        assertNotNull(deal.getId());
        assertEquals(DealStatus.DRAFT, deal.getStatus());

        // 2. Buyer creates mandate
        MandateDTO buyerMandate = new MandateDTO("mandate-1", deal.getId().toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of("figma", "source"), List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        MandateDTO created = mandateService.createMandate(deal.getId(), PartyRole.BUYER, buyerMandate);
        assertNotNull(created.mandateId());
        assertEquals(List.of("figma", "source"), created.mustHaveDeliverables());

        // 3. Seller creates mandate
        MandateDTO sellerMandate = new MandateDTO("mandate-2", deal.getId().toString(), PartyRole.SELLER, 1, "USD",
                70000L, 100000L, 10, 30, 5, 1,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        MandateDTO sellerCreated = mandateService.createMandate(deal.getId(), PartyRole.SELLER, sellerMandate);
        assertNotNull(sellerCreated.mandateId());

        // 4. Compute utilities for converging terms
        StructuredTermsDTO terms = new StructuredTermsDTO("Design sprint", DELIVERED, 80000L, "USD", 12, 2,
                "Lighthouse > 90");
        BigDecimal buyerUtility = UtilityCalculator.buyerUtility(buyerMandate, terms);
        BigDecimal sellerUtility = UtilityCalculator.sellerUtility(sellerMandate, terms);

        // Buyer: uP=(100000-80000)/(100000-80000)=1.0, uD=(30-12)/(30-10)=0.9, uR=(2-0)/(3-0)=0.6667
        // U_B = 0.6*1.0 + 0.25*0.9 + 0.15*0.6667 = 0.9250
        assertEquals(new BigDecimal("0.9250"), buyerUtility);
        // Seller: uP=(80000-70000)/(100000-70000)=0.3333, uD=(12-10)/(30-10)=0.1, uR=(5-2)/(5-1)=0.75
        // U_S = 0.6*0.3333 + 0.25*0.1 + 0.15*0.75 = 0.3375
        assertEquals(new BigDecimal("0.3375"), sellerUtility);

        // 5. Propose agreement (as buyer via controller)
        String proposeResponse = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(terms)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        AgreementSnapshotDTO snap = mapper.readValue(proposeResponse, AgreementSnapshotDTO.class);
        assertEquals(1, snap.version());
        assertNotNull(snap.termsHashSha256());
        assertEquals(64, snap.termsHashSha256().length());

        // Deal should now be TERMS_PENDING_ACCEPTANCE
        assertEquals(DealStatus.TERMS_PENDING_ACCEPTANCE,
                dealService.getDeal(deal.getId()).getStatus());

        // 6. Buyer accepts
        AgreementSnapshotDTO afterBuyer = agreementService.acceptAgreement(
                deal.getId(), snap.version(), snap.termsHashSha256(), "BUYER");
        assertNotNull(afterBuyer.buyerAcceptedAt());
        assertNull(afterBuyer.sellerAcceptedAt());
        assertNotEquals(DealStatus.AGREED, dealService.getDeal(deal.getId()).getStatus());

        // 7. Seller accepts → deal transitions to AGREED
        AgreementSnapshotDTO afterSeller = agreementService.acceptAgreement(
                deal.getId(), snap.version(), snap.termsHashSha256(), "SELLER");
        assertNotNull(afterSeller.buyerAcceptedAt());
        assertNotNull(afterSeller.sellerAcceptedAt());
        assertEquals(DealStatus.AGREED, dealService.getDeal(deal.getId()).getStatus());

        // 8. Stale version/hash rejection
        assertThrows(IllegalArgumentException.class,
                () -> agreementService.acceptAgreement(deal.getId(), snap.version(), "0".repeat(64), "BUYER"));
        assertThrows(IllegalArgumentException.class,
                () -> agreementService.acceptAgreement(deal.getId(), snap.version() + 1, snap.termsHashSha256(), "BUYER"));

        // 9. Mandate privacy: seller cannot retrieve buyer's mandate via GET /me as seller
        // (seller's own mandate is returned, not buyer's)
        MandateDTO sellerMyMandate = mandateService.getLatestMandate(deal.getId(), PartyRole.SELLER);
        assertEquals(PartyRole.SELLER, sellerMyMandate.partyRole());
        assertEquals(sellerCreated.mandateId(), sellerMyMandate.mandateId());
    }

    @Test
    void mandatePrivacy_noCrossAccountAccess() throws Exception {
        User buyer1 = makeUser("privacy-b1@test.com");
        User seller1 = makeUser("privacy-s1@test.com");
        User buyer2 = makeUser("privacy-b2@test.com");
        Deal deal = dealService.createDeal(buyer1.getId(), seller1.getId());

        MandateDTO dto = new MandateDTO("mandate-1", deal.getId().toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of("figma", "source"), List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        mandateService.createMandate(deal.getId(), PartyRole.BUYER, dto);

        // buyer2 (not a participant) cannot access the mandate
        mockMvc.perform(get("/api/v1/deals/{dealId}/mandates/me", deal.getId())
                        .with(user(userDetails(buyer2)))
                        .requestAttr("userId", buyer2.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void mandateValidation_rejectsInvalidData() {
        User buyer = makeUser("val-b@test.com");
        User seller = makeUser("val-s@test.com");
        Deal deal = dealService.createDeal(buyer.getId(), seller.getId());

        // Buyer: limitPrice < targetPrice → invalid
        MandateDTO invalidBuyer = new MandateDTO("mandate-1", deal.getId().toString(), PartyRole.BUYER, 1, "USD",
                70000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.BUYER, invalidBuyer));

        // Seller: limitPrice > targetPrice → invalid
        MandateDTO invalidSeller = new MandateDTO("mandate-2", deal.getId().toString(), PartyRole.SELLER, 1, "USD",
                110000L, 100000L, 10, 30, 5, 1,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.SELLER, invalidSeller));

        // Version 0 → invalid
        MandateDTO invalidVersion = new MandateDTO("mandate-3", deal.getId().toString(), PartyRole.BUYER, 0, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.BUYER, invalidVersion));

        // Past expiry → invalid
        MandateDTO invalidExpiry = new MandateDTO("mandate-4", deal.getId().toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2020-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.BUYER, invalidExpiry));
    }
}
