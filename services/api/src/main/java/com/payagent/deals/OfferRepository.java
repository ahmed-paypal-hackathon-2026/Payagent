// Disabled to avoid duplicate JPA mapping and Spring Data bean creation
// package com.payagent.deals;
// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.jpa.repository.Query;
// import org.springframework.stereotype.Repository;
// import java.util.List;
// import java.util.Optional;
// import java.util.UUID;
// // @Repository disabled to avoid duplicate bean definition
// public interface OfferRepository extends JpaRepository<Offer, UUID> {
//     List<Offer> findByDealIdOrderByRoundNumberDesc(UUID dealId);
//     @Query("SELECT MAX(o.roundNumber) FROM Offer o WHERE o.dealId = ?1")
//     Integer findMaxRoundNumberByDealId(UUID dealId);
// }
