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

    private Mandate newMandate(UUID dealId, PartyRole role, int version, long limitPrice, long targetPrice) {
        Mandate m = new Mandate(
                UUID.randomUUID(), dealId, role, version, "USD",
                limitPrice, targetPrice, 30, 10, 0, 3,
                List.of("figma", "source"), List.of("resale"),
                NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
        m.validate();
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
    void validateRejectsVersionLessThanOne() {
        Mandate m = new Mandate(
                UUID.randomUUID(), UUID.randomUUID(), PartyRole.BUYER, 0, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
        assertThrows(IllegalArgumentException.class, m::validate);
    }

    @Test
    void validateRejectsPastExpiry() {
        Mandate m = new Mandate(
                UUID.randomUUID(), UUID.randomUUID(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().minusDays(1));
        assertThrows(IllegalArgumentException.class, m::validate);
    }

    @Test
    void validateRejectsBuyerLimitLessThanTarget() {
        Mandate m = new Mandate(
                UUID.randomUUID(), UUID.randomUUID(), PartyRole.BUYER, 1, "USD",
                70000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
        assertThrows(IllegalArgumentException.class, m::validate);
    }

    @Test
    void validateRejectsSellerLimitGreaterThanTarget() {
        Mandate m = new Mandate(
                UUID.randomUUID(), UUID.randomUUID(), PartyRole.SELLER, 1, "USD",
                110000L, 100000L, 10, 30, 5, 1,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
        assertThrows(IllegalArgumentException.class, m::validate);
    }

    @Test
    void validateAcceptsValidBuyerMandate() {
        Mandate m = new Mandate(
                UUID.randomUUID(), UUID.randomUUID(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
        assertDoesNotThrow(m::validate);
    }

    @Test
    void validateAcceptsValidSellerMandate() {
        Mandate m = new Mandate(
                UUID.randomUUID(), UUID.randomUUID(), PartyRole.SELLER, 1, "USD",
                70000L, 100000L, 10, 30, 5, 1,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, OffsetDateTime.now().plusDays(30));
        assertDoesNotThrow(m::validate);
    }

    private static UUID savedId(Mandate m) {
        return m.getId();
    }
}
