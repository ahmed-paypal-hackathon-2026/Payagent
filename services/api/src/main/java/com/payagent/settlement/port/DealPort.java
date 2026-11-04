package com.payagent.settlement.port;

import com.payagent.common.contracts.AgreementSnapshotDTO;
import com.payagent.common.contracts.DealStatus;
import java.util.UUID;

public interface DealPort {

    AgreementSnapshotDTO getAgreedAgreement(UUID dealId);

    DealStatus getDealStatus(UUID dealId);

    void assertBuyer(UUID dealId, String callerEmail);

    void assertParty(UUID dealId, String callerEmail);

    void transitionDealStatus(UUID dealId, DealStatus targetStatus);

    boolean isDealDisputed(UUID dealId);

    String getBuyerEmail(UUID dealId);

    String getSellerEmail(UUID dealId);
}
