package com.payagent.mandates;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MandateRepository extends JpaRepository<Mandate, UUID> {
    Optional<Mandate> findTopByDealIdAndPartyRoleOrderByVersionDesc(UUID dealId, com.payagent.common.contracts.PartyRole partyRole);
    Optional<Mandate> findByDealIdAndPartyRoleAndVersion(UUID dealId, com.payagent.common.contracts.PartyRole partyRole, Integer version);
}
