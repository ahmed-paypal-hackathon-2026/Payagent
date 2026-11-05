package com.payagent.mandates;

import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MandateRepositoryTest {

    @Autowired private MandateRepository repository;

    @Autowired private MandateValidator mandateValidator;

    private Mandate newMandate(UUID dealId, PartyRole role, int version, long limitPrice, long targetPrice) {
        Mandate m = new Mandate(
                UUID.randomUUID(), dealId, role, version, "USD",
                limitPrice, targetPrice, 30, 10, 0, 3,
                Mandate.toJson(List.of("figma", "source")), Mandate.toJson(List.of("resale")),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
        return repository.saveAndFlush(m);
    }

    @Test
    void saveAndFindByDealIdPartyRoleVersion() {
        UUID dealId = UUID.randomUUID();
        Mandate saved = newMandate(dealId, PartyRole.BUYER, 1, 100000L, 80000L);

        Mandate found = repository.findByDealIdAndPartyRoleAndVersion(dealId, PartyRole.BUYER, 1)
                .orElseThrow(() -> new AssertionError("Mandate not found"));

        assertEquals(saved.getId(), found.getId());
        assertEquals(PartyRole.BUYER, found.getPartyRole());
        assertEquals(Integer.valueOf(1), found.getVersion());
        assertEquals(List.of("figma", "source"), Mandate.fromJson(found.getMustHaveDeliverablesJson()));
        assertEquals(List.of("resale"), Mandate.fromJson(found.getForbiddenTermsJson()));
    }

    @Test
    void findTopByDealIdAndPartyRoleOrderByVersionDesc() {
        UUID dealId = UUID.randomUUID();
        newMandate(dealId, PartyRole.BUYER, 1, 100000L, 80000L);
        Mandate v2 = newMandate(dealId, PartyRole.BUYER, 2, 100000L, 80000L);

        Mandate latest = repository.findTopByDealIdAndPartyRoleOrderByVersionDesc(dealId, PartyRole.BUYER)
                .orElseThrow(() -> new AssertionError("Latest mandate not found"));

        assertEquals(savedId(v2), latest.getId());
        assertEquals(Integer.valueOf(2), latest.getVersion());
    }

    @Test
    void uniqueConstraintRejectsDuplicateVersion() {
        UUID dealId = UUID.randomUUID();
        newMandate(dealId, PartyRole.BUYER, 1, 100000L, 80000L);

        assertThrows(DataIntegrityViolationException.class,
                () -> newMandate(dealId, PartyRole.BUYER, 1, 100000L, 80000L));
    }

    @Test
    void sameVersionDifferentRoleIsAllowed() {
        UUID dealId = UUID.randomUUID();
        Mandate buyer = newMandate(dealId, PartyRole.BUYER, 1, 100000L, 80000L);
        Mandate seller = newMandate(dealId, PartyRole.SELLER, 1, 70000L, 100000L);

        assertNotEquals(buyer.getId(), seller.getId());
        assertEquals(PartyRole.BUYER, buyer.getPartyRole());
        assertEquals(PartyRole.SELLER, seller.getPartyRole());
    }


    @Test
    void validateRejectsPastExpiry() {
        MandateService.MandateCreateRequest req = new MandateService.MandateCreateRequest(
                "USD", 100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> mandateValidator.validate(req, PartyRole.BUYER));
    }

    @Test
    void validateRejectsBuyerLimitLessThanTarget() {
        MandateService.MandateCreateRequest req = createRequest(70000L, 80000L, 30, 10, 0, 3);
        assertThrows(IllegalArgumentException.class, () -> mandateValidator.validate(req, PartyRole.BUYER));
    }

    @Test
    void validateRejectsSellerLimitGreaterThanTarget() {
        MandateService.MandateCreateRequest req = createRequest(110000L, 100000L, 10, 30, 5, 1);
        assertThrows(IllegalArgumentException.class, () -> mandateValidator.validate(req, PartyRole.SELLER));
    }

    @Test
    void validateAcceptsValidBuyerMandate() {
        MandateService.MandateCreateRequest req = createRequest(100000L, 80000L, 30, 10, 0, 3);
        assertDoesNotThrow(() -> mandateValidator.validate(req, PartyRole.BUYER));
    }

    @Test
    void validateAcceptsValidSellerMandate() {
        MandateService.MandateCreateRequest req = createRequest(70000L, 100000L, 10, 30, 5, 1);
        assertDoesNotThrow(() -> mandateValidator.validate(req, PartyRole.SELLER));
    }

    private static MandateService.MandateCreateRequest createRequest(
            long limitPrice, long targetPrice, int limitDelivery, int targetDelivery, int limitRevisions, int targetRevisions) {
        return new MandateService.MandateCreateRequest(
                "USD", limitPrice, targetPrice, limitDelivery, targetDelivery, limitRevisions, targetRevisions,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
    }

    private static UUID savedId(Mandate m) {
        return m.getId();
    }
}
