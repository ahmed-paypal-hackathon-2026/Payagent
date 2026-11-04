package com.payagent.mandates;

import com.payagent.common.contracts.PartyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MandateRepository extends JpaRepository<Mandate, UUID> {

    Optional<Mandate> findByDealIdAndPartyRoleAndVersion(UUID dealId, PartyRole partyRole, Integer version);

    Optional<Mandate> findTopByDealIdAndPartyRoleOrderByVersionDesc(UUID dealId, PartyRole partyRole);
}
