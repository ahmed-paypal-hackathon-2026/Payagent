package com.payagent.settlement.port;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import java.util.UUID;

public interface DealPort {

    AgreementSnapshotDTO getAgreedAgreement(UUID dealId);

    DealStatus getDealStatus(UUID dealId);

    void assertBuyer(UUID dealId, String callerEmail);

    void assertSeller(UUID dealId, String callerEmail);

    void assertParty(UUID dealId, String callerEmail);

    void transitionDealStatus(UUID dealId, DealStatus targetStatus);

    /**
     * Acquires a row-level lock on the deal and returns its current status.
     * Used to serialize capture against concurrent dispute creation.
     */
    DealStatus lockDealAndGetStatus(UUID dealId);

    boolean hasOpenDispute(UUID dealId);

    boolean isDealDisputed(UUID dealId);

    String getBuyerEmail(UUID dealId);

    String getSellerEmail(UUID dealId);
}
