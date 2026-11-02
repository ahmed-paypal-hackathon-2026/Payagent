package com.payagent.deals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findByDealIdOrderByRoundNumberDesc(UUID dealId);

    List<Offer> findByDealIdOrderByRoundNumberAsc(UUID dealId);

    @Query("SELECT MAX(o.roundNumber) FROM Offer o WHERE o.dealId = ?1")
    Integer findMaxRoundNumberByDealId(UUID dealId);
}
