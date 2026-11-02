package com.payagent.mandates;

import com.payagent.common.contracts.DealStatus;
import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.deals.Deal;
import com.payagent.deals.DealRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MandateServiceTest {

    @Autowired
    private MandateService mandateService;

    @Autowired
    private DealRepository dealRepository;

    @Test
    void serviceExists() {
        assertNotNull(MandateService.class);
    }

    @Test
    void createMandateReturnsScopedMandateWithPersistedTerms() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID dealId = dealRepository.save(new Deal(UUID.randomUUID(), buyerId, sellerId, DealStatus.ACTIVE_NEGOTIATION)).getId();

        MandateService.MandateCreateRequest request = new MandateService.MandateCreateRequest(
                "USD", 120_000L, 90_000L, 20, 15, 2, 1,
                List.of("core"), List.of("custom-tooling"),
                NegotiationMode.ASSISTED, 10, OffsetDateTime.now().plusYears(1)
        );

        MandateDTO created = mandateService.createMandate(dealId, buyerId, request);
        MandateDTO loaded = mandateService.getMandateForUser(dealId, buyerId);

        assertEquals(PartyRole.BUYER, loaded.partyRole());
        assertEquals(1, loaded.version());
        assertEquals(List.of("core"), created.mustHaveDeliverables());
        assertEquals(List.of("custom-tooling"), created.forbiddenTerms());
        assertEquals(List.of("core"), loaded.mustHaveDeliverables());
        assertEquals(List.of("custom-tooling"), loaded.forbiddenTerms());
    }
}
