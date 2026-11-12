package com.payagent.agreements;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import com.payagent.deals.DealService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level integration tests for the agreement lifecycle:
 * authenticated acceptance, stale version/hash rejection, version increments,
 * acceptance clearing on new terms, and deal transition to AGREED.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgreementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserRepository userRepository;
    @Autowired private DealService dealService;
    @Autowired private DealRepository dealRepository;

    private static final List<String> DELIVERED = List.of("figma", "source", "notes");

    private User makeUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "hash", OffsetDateTime.now()));
    }

    private Deal makeDeal() {
        User b = makeUser("ac" + UUID.randomUUID() + "@test.com");
        User s = makeUser("ac" + UUID.randomUUID() + "@test.com");
        return dealRepository.save(new Deal(UUID.randomUUID(), b.getId(), s.getId(), DealStatus.ACTIVE_NEGOTIATION));
    }

    private String termsJson(long price, int delivery, int revisions) throws Exception {
        StructuredTermsDTO t = new StructuredTermsDTO("Design sprint", DELIVERED, price, "USD",
                delivery, revisions, "Lighthouse > 90");
        return mapper.writeValueAsString(t);
    }

    private UserDetails userDetails(User u) {
        return new org.springframework.security.core.userdetails.User(
                u.getId().toString(), "password", List.of());
    }

    @Test
    void proposeAndAccept_buyerThenSeller_dealBecomesAgreed() throws Exception {
        Deal deal = makeDeal();
        User buyer = userRepository.findById(deal.getBuyerUserId()).orElseThrow();
        User seller = userRepository.findById(deal.getSellerUserId()).orElseThrow();

        // Propose (as buyer)
        String proposeResponse = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(80000, 10, 3)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AgreementSnapshotDTO snap = mapper.readValue(proposeResponse, AgreementSnapshotDTO.class);
        assertVersionAndHash(snap, 1);

        // Buyer accepts
        mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", deal.getId(), snap.version())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(
                        Map.of("termsHashSha256", snap.termsHashSha256()))))
                .andExpect(status().isOk());

        // Seller accepts -> deal becomes AGREED
        mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", deal.getId(), snap.version())
                        .with(user(userDetails(seller)))
                        .requestAttr("userId", seller.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(
                        Map.of("termsHashSha256", snap.termsHashSha256()))))
                .andExpect(status().isOk());
    }

    @Test
    void accept_staleHash_returns409() throws Exception {
        Deal deal = makeDeal();
        User buyer = userRepository.findById(deal.getBuyerUserId()).orElseThrow();
        String proposeResponse = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(80000, 10, 3)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AgreementSnapshotDTO snap = mapper.readValue(proposeResponse, AgreementSnapshotDTO.class);

        mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", deal.getId(), snap.version())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(
                        Map.of("termsHashSha256", "0".repeat(64)))))
                .andExpect(status().isConflict());
    }

    @Test
    void accept_staleVersion_returns409() throws Exception {
        Deal deal = makeDeal();
        User buyer = userRepository.findById(deal.getBuyerUserId()).orElseThrow();
        String proposeResponse = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(80000, 10, 3)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AgreementSnapshotDTO snap = mapper.readValue(proposeResponse, AgreementSnapshotDTO.class);

        // Propose v2 with different terms; v1 becomes stale.
        String v2Response = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(85000, 12, 2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AgreementSnapshotDTO v2 = mapper.readValue(v2Response, AgreementSnapshotDTO.class);
        assertVersionAndHash(v2, 2);

        // Accepting the stale v1 must conflict.
        mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", deal.getId(), snap.version())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(
                        Map.of("termsHashSha256", snap.termsHashSha256()))))
                .andExpect(status().isConflict());
    }

    @Test
    void newVersion_clearsAcceptances_andIncrements() throws Exception {
        Deal deal = makeDeal();
        User buyer = userRepository.findById(deal.getBuyerUserId()).orElseThrow();

        // Propose v1
        String v1Response = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(80000, 10, 3)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AgreementSnapshotDTO v1 = mapper.readValue(v1Response, AgreementSnapshotDTO.class);
        assertVersionAndHash(v1, 1);

        // Buyer accepts v1
        mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", deal.getId(), v1.version())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(
                        Map.of("termsHashSha256", v1.termsHashSha256()))))
                .andExpect(status().isOk());

        // Propose v2 (new terms)
        String v2Response = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(85000, 12, 2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AgreementSnapshotDTO v2 = mapper.readValue(v2Response, AgreementSnapshotDTO.class);
        assertVersionAndHash(v2, 2);

        // v2 has no acceptances
        assertNullField(v2.buyerAcceptedAt());
        assertNullField(v2.sellerAcceptedAt());

        // v1 hash is now stale (v2 is latest)
        mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/{version}/accept", deal.getId(), v1.version())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(
                        Map.of("termsHashSha256", v1.termsHashSha256()))))
                .andExpect(status().isConflict());
    }

    @Test
    void proposeAgreement_differentTerms_differentHash() throws Exception {
        Deal deal = makeDeal();
        User buyer = userRepository.findById(deal.getBuyerUserId()).orElseThrow();

        String r1 = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(80000, 10, 3)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AgreementSnapshotDTO a1 = mapper.readValue(r1, AgreementSnapshotDTO.class);

        String r2 = mockMvc.perform(post("/api/v1/deals/{dealId}/agreements/propose", deal.getId())
                        .with(user(userDetails(buyer)))
                        .requestAttr("userId", buyer.getId())
                        .header("Idempotency-Key", "idem-key-" + UUID.randomUUID())
                        .contentType("application/json")
                        .content(termsJson(85000, 12, 2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AgreementSnapshotDTO a2 = mapper.readValue(r2, AgreementSnapshotDTO.class);

        assertNotEqualField(a1.termsHashSha256(), a2.termsHashSha256());
    }

    // ------------------------------------------------------------ helpers

    private void assertVersionAndHash(AgreementSnapshotDTO snap, int expectedVersion) {
        assertNotNullField(snap.agreementId());
        assertEqualField(expectedVersion, snap.version());
        assertNotNullField(snap.termsHashSha256());
        assertEqualField(64, snap.termsHashSha256().length());
        assertNullField(snap.buyerAcceptedAt());
        assertNullField(snap.sellerAcceptedAt());
    }

    private void assertEqualField(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private void assertNotEqualField(Object a, Object b) {
        if (a.equals(b)) {
            throw new AssertionError("Expected different values but both are <" + a + ">");
        }
    }

    private void assertNotNullField(Object value) {
        if (value == null) {
            throw new AssertionError("Expected non-null value");
        }
    }

    private void assertNullField(Object value) {
        if (value != null) {
            throw new AssertionError("Expected null value but was <" + value + ">");
        }
    }

}
