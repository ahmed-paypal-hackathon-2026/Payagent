package com.payagent.mandates;

import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UtilityCalculatorTest {
    private final UtilityCalculator calc = new UtilityCalculator();

    private MandateDTO buyerMandate() {
        return new MandateDTO("m1","d1",PartyRole.BUYER,1,"USD",120000L,90000L,20,15,2,4,List.of("core"),List.of(),NegotiationMode.ASSISTED,10,"2027-01-01T00:00:00Z");
    }

    private MandateDTO sellerMandate() {
        return new MandateDTO("m2","d1",PartyRole.SELLER,1,"USD",85000L,115000L,10,12,5,3,List.of(),List.of(),NegotiationMode.ASSISTED,10,"2027-01-01T00:00:00Z");
    }

    @Test
    void buyerVetoPriceTooHigh() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("core"),130000L,"USD",10,4,"ok");
        assertEquals(BigDecimal.valueOf(-1), calc.buyerUtility(m, t));
    }

    @Test
    void buyerVetoDeliveryTooHigh() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("core"),100000L,"USD",25,4,"ok");
        assertEquals(BigDecimal.valueOf(-1), calc.buyerUtility(m, t));
    }

    @Test
    void buyerVetoRevisionsTooLow() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("core"),100000L,"USD",10,1,"ok");
        assertEquals(BigDecimal.valueOf(-1), calc.buyerUtility(m, t));
    }

    @Test
    void buyerVetoScopeMissing() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("other"),100000L,"USD",10,4,"ok");
        assertEquals(BigDecimal.valueOf(-1), calc.buyerUtility(m, t));
    }

    @Test
    void buyerUtilityAtTargetGivesOne() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("core"),90000L,"USD",15,4,"ok");
        BigDecimal u = calc.buyerUtility(m, t);
        assertEquals(0, u.compareTo(BigDecimal.ONE));
    }

    @Test
    void buyerUtilityMidPoint() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("core"),105000L,"USD",17,3,"ok");
        BigDecimal u = calc.buyerUtility(m, t);
        // price 0.5, delivery 0.6, revisions 0.5 => weighted 0.525
        assertEquals(0, u.compareTo(new BigDecimal("0.5250")));
    }

    @Test
    void sellerVetoPriceTooLow() {
        MandateDTO m = sellerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of(),80000L,"USD",12,3,"ok");
        assertEquals(BigDecimal.valueOf(-1), calc.sellerUtility(m, t));
    }

    @Test
    void sellerVetoDeliveryTooLow() {
        MandateDTO m = sellerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of(),100000L,"USD",8,3,"ok");
        assertEquals(BigDecimal.valueOf(-1), calc.sellerUtility(m, t));
    }

    @Test
    void sellerVetoRevisionsTooHigh() {
        MandateDTO m = sellerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of(),100000L,"USD",12,6,"ok");
        assertEquals(BigDecimal.valueOf(-1), calc.sellerUtility(m, t));
    }

    @Test
    void sellerUtilityAtTargetGivesOne() {
        MandateDTO m = sellerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of(),115000L,"USD",12,3,"ok");
        BigDecimal u = calc.sellerUtility(m, t);
        assertEquals(0, u.compareTo(BigDecimal.ONE));
    }

    @Test
    void clampingPreventsAboveOne() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("core"),80000L,"USD",10,5,"ok");
        BigDecimal u = calc.buyerUtility(m, t);
        assertTrue(u.compareTo(BigDecimal.ONE) <= 0);
    }

    @Test
    void roundingToScale4() {
        MandateDTO m = buyerMandate();
        StructuredTermsDTO t = new StructuredTermsDTO("t",List.of("core"),95000L,"USD",16,3,"ok");
        BigDecimal u = calc.buyerUtility(m, t);
        assertEquals(4, u.scale());
    }
}
