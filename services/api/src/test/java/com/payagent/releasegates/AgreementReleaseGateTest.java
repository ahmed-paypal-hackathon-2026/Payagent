package com.payagent.releasegates;

import com.payagent.agreements.*;
import com.payagent.common.contracts.*;
import com.payagent.deals.DealService;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class AgreementReleaseGateTest {
    @Test
    void tst02_oneCentEditInvalidatesConsentAndOldVersionCannotBeAccepted() {
        AgreementRepository repository = mock(AgreementRepository.class);
        DealService deals = mock(DealService.class);
        AtomicReference<Agreement> latest = new AtomicReference<>();
        Map<Integer, Agreement> versions = new HashMap<>();
        UUID dealId = UUID.randomUUID();
        when(repository.findTopByDealIdOrderByVersionDesc(dealId)).thenAnswer(i -> Optional.ofNullable(latest.get()));
        when(repository.save(any(Agreement.class))).thenAnswer(i -> {
            Agreement a = i.getArgument(0);
            versions.put(a.getVersion(), a);
            latest.set(a);
            return a;
        });
        when(repository.findByDealIdAndVersion(eq(dealId), anyInt()))
                .thenAnswer(i -> Optional.ofNullable(versions.get(i.getArgument(1))));
        AgreementService service = new AgreementService(repository, deals);
        StructuredTermsDTO original = terms(98000);
        AgreementSnapshotDTO v1 = service.proposeAgreement(dealId, original);
        service.acceptAgreement(dealId, 1, v1.termsHashSha256(), "BUYER");
        assertNotNull(latest.get().getBuyerAcceptedAt());
        AgreementSnapshotDTO v2 = service.proposeNewVersion(dealId, terms(98001));
        assertEquals(2, v2.version());
        assertNotEquals(v1.termsHashSha256(), v2.termsHashSha256());
        assertNull(v2.buyerAcceptedAt());
        assertNull(v2.sellerAcceptedAt());
        assertNull(versions.get(1).getBuyerAcceptedAt());
        assertThrows(AgreementConflictException.class, () -> service.acceptAgreement(dealId, 1, v1.termsHashSha256(), "SELLER"));
        assertThrows(AgreementConflictException.class, () -> service.acceptAgreement(dealId, 2, v1.termsHashSha256(), "SELLER"));
        verify(deals, never()).setStatus(dealId, DealStatus.AGREED);
    }

    private StructuredTermsDTO terms(long cents) {
        return new StructuredTermsDTO("Release fixture", List.of("source code"), cents, "USD", 7, 2, "Build succeeds");
    }
}
