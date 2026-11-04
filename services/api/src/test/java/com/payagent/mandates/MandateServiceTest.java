package com.payagent.mandates;

import com.payagent.auth.User;
import com.payagent.auth.UserRepository;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import com.payagent.deals.Deal;
import com.payagent.deals.DealService;
import org.junit.jupiter.api.Test;
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
class MandateServiceTest {

    @Autowired private MandateService mandateService;
    @Autowired private DealService dealService;
    @Autowired private UserRepository userRepository;

    private User makeUser(String email) {
        return userRepository.save(new User(UUID.randomUUID(), email, "hash", OffsetDateTime.now()));
    }

    private Deal makeDeal(User buyer, User seller) {
        return dealService.createDeal(buyer.getId(), seller.getId());
    }

    private MandateDTO buyerDto(UUID dealId) {
        return new MandateDTO(null, dealId.toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of("figma", "source"), List.of("resale"),
                NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
    }

    @Test
    void createAndGet_roundTripsLists() {
        User buyer = makeUser("mb@test.com");
        User seller = makeUser("ms@test.com");
        Deal deal = makeDeal(buyer, seller);

        MandateDTO created = mandateService.createMandate(deal.getId(), PartyRole.BUYER, buyerDto(deal.getId()));
        assertNotNull(created.mandateId());
        assertEquals(List.of("figma", "source"), created.mustHaveDeliverables());
        assertEquals(List.of("resale"), created.forbiddenTerms());

        MandateDTO latest = mandateService.getLatestMandate(deal.getId(), PartyRole.BUYER);
        assertEquals(created.mandateId(), latest.mandateId());
        assertEquals(List.of("figma", "source"), latest.mustHaveDeliverables());
        assertEquals(List.of("resale"), latest.forbiddenTerms());
    }

    @Test
    void createMandate_rejectsVersionLessThanOne() {
        User buyer = makeUser("mb2@test.com");
        User seller = makeUser("ms2@test.com");
        Deal deal = makeDeal(buyer, seller);
        MandateDTO dto = new MandateDTO(null, deal.getId().toString(), PartyRole.BUYER, 0, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.BUYER, dto));
    }

    @Test
    void createMandate_rejectsPastExpiry() {
        User buyer = makeUser("mb3@test.com");
        User seller = makeUser("ms3@test.com");
        Deal deal = makeDeal(buyer, seller);
        MandateDTO dto = new MandateDTO(null, deal.getId().toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2020-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.BUYER, dto));
    }

    @Test
    void createMandate_rejectsBuyerLimitLessThanTarget() {
        User buyer = makeUser("mb4@test.com");
        User seller = makeUser("ms4@test.com");
        Deal deal = makeDeal(buyer, seller);
        MandateDTO dto = new MandateDTO(null, deal.getId().toString(), PartyRole.BUYER, 1, "USD",
                70000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.BUYER, dto));
    }

    @Test
    void createMandate_rejectsDuplicateVersion() {
        User buyer = makeUser("mb5@test.com");
        User seller = makeUser("ms5@test.com");
        Deal deal = makeDeal(buyer, seller);
        mandateService.createMandate(deal.getId(), PartyRole.BUYER, buyerDto(deal.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.createMandate(deal.getId(), PartyRole.BUYER, buyerDto(deal.getId())));
    }

    @Test
    void getLatestMandate_throwsWhenMissing() {
        User buyer = makeUser("mb6@test.com");
        User seller = makeUser("ms6@test.com");
        Deal deal = makeDeal(buyer, seller);
        assertThrows(IllegalArgumentException.class,
                () -> mandateService.getLatestMandate(deal.getId(), PartyRole.BUYER));
    }

    @Test
    void differentVersions_areIndependent() {
        User buyer = makeUser("mi3@test.com");
        User seller = makeUser("mi4@test.com");
        Deal deal = makeDeal(buyer, seller);

        MandateDTO v1 = new MandateDTO(null, deal.getId().toString(), PartyRole.BUYER, 1, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        MandateDTO v2 = new MandateDTO(null, deal.getId().toString(), PartyRole.BUYER, 2, "USD",
                100000L, 80000L, 30, 10, 0, 3,
                List.of(), List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");

        MandateDTO created1 = mandateService.createMandate(deal.getId(), PartyRole.BUYER, v1);
        MandateDTO created2 = mandateService.createMandate(deal.getId(), PartyRole.BUYER, v2);

        assertEquals(1, created1.version());
        assertEquals(2, created2.version());
    }
}
