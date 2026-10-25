package com.payagent.deals;

import com.payagent.common.contracts.DealStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DealServiceTest {

    @Autowired
    private DealService dealService;

    @Autowired
    private DealRepository repository;

    private Deal createDeal(DealStatus status) {
        Deal deal = new Deal(UUID.randomUUID(), UUID.randomUUID(), null, status);
        return repository.save(deal);
    }

    @Test
    void setStatus_legalTransition_persists() {
        Deal deal = createDeal(DealStatus.DRAFT);
        dealService.setStatus(deal.getId(), DealStatus.INVITED);
        Deal persisted = repository.findById(deal.getId()).orElseThrow();
        assertEquals(DealStatus.INVITED, persisted.getStatus());
    }

    @Test
    void setStatus_illegalTransition_throwsAndLeavesUnchanged() {
        Deal deal = createDeal(DealStatus.DRAFT);
        assertThrows(IllegalArgumentException.class,
                () -> dealService.setStatus(deal.getId(), DealStatus.AGREED));
        Deal persisted = repository.findById(deal.getId()).orElseThrow();
        assertEquals(DealStatus.DRAFT, persisted.getStatus());
    }

    @Test
    void setStatus_missingDeal_throws() {
        UUID randomId = UUID.randomUUID();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dealService.setStatus(randomId, DealStatus.INVITED));
        assertTrue(ex.getMessage().contains("Deal not found"));
    }
}
