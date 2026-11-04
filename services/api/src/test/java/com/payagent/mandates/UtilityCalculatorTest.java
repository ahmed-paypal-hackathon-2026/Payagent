package com.payagent.mandates;

import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.NegotiationMode;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link UtilityCalculator} derived from plan Section 2.
 *
 * <p>Default fixture (used throughout):
 * <ul>
 *   <li>BUYER: Pmax=100000, Ptarget=80000, Dmax=30, Dtarget=10, Rmin=0, Rtarget=3</li>
 *   <li>SELLER: Pmin=70000, Ptarget=100000, Dmin=10, Dtarget=30, Rmax=5, Rtarget=1</li>
 *   <li>weights: wP=0.60, wD=0.25, wR=0.15</li>
 * </ul>
 */
class UtilityCalculatorTest {

    private static final List<String> SCOPE = List.of("figma", "source");
    private static final List<String> DELIVERED = List.of("figma", "source", "notes");

    // ------------------------------------------------------------- fixtures

    private static MandateDTO buyerMandate() {
        return buyerMandate(100000L, 80000L, 30, 10, 0, 3);
    }

    private static MandateDTO buyerMandate(long limitPrice, long targetPrice,
                                           int limitDelivery, int targetDelivery,
                                           int limitRevisions, int targetRevisions) {
        return new MandateDTO(
                "m-1", "deal-1", PartyRole.BUYER, 1, "USD",
                limitPrice, targetPrice, limitDelivery, targetDelivery,
                limitRevisions, targetRevisions,
                SCOPE, List.of("resale"), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
    }

    private static MandateDTO sellerMandate() {
        return new MandateDTO(
                "m-2", "deal-1", PartyRole.SELLER, 1, "USD",
                70000L, 100000L, 10, 30,
                5, 1,
                SCOPE, List.of("exclusivity"), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
    }

    private static MandateDTO sellerMandate(long limitPrice, long targetPrice,
                                            int limitDelivery, int targetDelivery,
                                            int limitRevisions, int targetRevisions) {
        return new MandateDTO(
                "m-2", "deal-1", PartyRole.SELLER, 1, "USD",
                limitPrice, targetPrice, limitDelivery, targetDelivery,
                limitRevisions, targetRevisions,
                SCOPE, List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
    }

    private static StructuredTermsDTO terms(long price, int delivery, int revisions) {
        return new StructuredTermsDTO(
                "Design sprint", DELIVERED, price, "USD", delivery, revisions, "Lighthouse > 90");
    }

    private static void assertScore(String expected, BigDecimal actual) {
        assertEquals(new BigDecimal(expected), actual,
                "expected " + expected + " but was " + actual);
    }

    // ---------------------------------------------------------------- buyer

    @Test
    @DisplayName("TC-B1 buyer at target scores 1.0000")
    void buyerAtTargetScoresOne() {
        assertScore("1.0000", UtilityCalculator.buyerUtility(buyerMandate(), terms(80000, 10, 3)));
    }

    @Test
    @DisplayName("TC-B2 buyer at limit scores 0.0000")
    void buyerAtLimitScoresZero() {
        assertScore("0.0000", UtilityCalculator.buyerUtility(buyerMandate(), terms(100000, 30, 0)));
    }

    @Test
    @DisplayName("TC-B3 buyer midpoint scores 0.4750")
    void buyerMidpointScoresCorrectly() {
        // uP=0.5000, uD=0.5000, uR=0.3333 -> 0.6*0.5 + 0.25*0.5 + 0.15*0.3333 = 0.4750
        assertScore("0.4750", UtilityCalculator.buyerUtility(buyerMandate(), terms(90000, 20, 1)));
    }

    @Test
    @DisplayName("TC-B4 price above buyer limit is a veto")
    void buyerPriceVeto() {
        assertEquals(UtilityCalculator.VETO,
                UtilityCalculator.buyerUtility(buyerMandate(), terms(100001, 10, 3)));
    }

    @Test
    @DisplayName("TC-B5 delivery above buyer limit is a veto")
    void buyerDeliveryVeto() {
        assertEquals(UtilityCalculator.VETO,
                UtilityCalculator.buyerUtility(buyerMandate(), terms(90000, 31, 3)));
    }

    @Test
    @DisplayName("TC-B6 revisions below buyer minimum is a veto")
    void buyerRevisionsVeto() {
        MandateDTO mandate = buyerMandate(100000L, 80000L, 30, 10, 2, 5);
        assertEquals(UtilityCalculator.VETO,
                UtilityCalculator.buyerUtility(mandate, terms(90000, 10, 1)));
    }

    @Test
    @DisplayName("TC-B7 zero denominator guard returns 1.0000 for the exact acceptable value")
    void buyerZeroDenominatorGuard() {
        MandateDTO mandate = buyerMandate(90000L, 90000L, 15, 15, 2, 2);
        assertScore("1.0000", UtilityCalculator.buyerUtility(mandate, terms(90000, 15, 2)));
    }

    @Test
    @DisplayName("TC-B8 missing mandatory scope deliverable is a buyer veto (S = 0)")
    void buyerMissingScopeVeto() {
        StructuredTermsDTO incomplete =
                new StructuredTermsDTO("Design sprint", List.of("figma"), 80000, "USD", 10, 3, "x");
        assertEquals(UtilityCalculator.VETO, UtilityCalculator.buyerUtility(buyerMandate(), incomplete));
    }

    @Test
    @DisplayName("TC-B9 buyer weighted sum rounds HALF_UP at scale 4")
    void buyerRoundingHalvesUp() {
        // uP=6667/20000=0.33335 -> HALF_UP -> 0.3334, uD=15/20=0.7500, uR=1/3=0.3333
        // 0.6*0.3334 + 0.25*0.7500 + 0.15*0.3333 = 0.437535 -> 0.4375
        assertScore("0.4375", UtilityCalculator.buyerUtility(buyerMandate(), terms(93333, 15, 1)));
    }

    @Test
    @DisplayName("TC-B10 utility is always clamped to [0,1] even for out-of-range input")
    void buyerClamping() {
        assertScore("1.0000", UtilityCalculator.buyerUtility(buyerMandate(), terms(0, 0, 3)));
        assertScore("0.0000", UtilityCalculator.buyerUtility(buyerMandate(), terms(100000, 30, 0)));
    }

    @Test
    @DisplayName("TC-B12 buyer component rounding is HALF_UP at scale 4")
    void buyerComponentRoundingIsHalfUp() {
        // uP = 1/20000 = 0.00005 exactly (half-point) -> HALF_UP 0.0001 / HALF_DOWN 0.0000
        // uD = uR = 1.0000; 0.6*0.0001 + 0.4 = 0.40006 -> 0.4001
        assertScore("0.4001", UtilityCalculator.buyerUtility(buyerMandate(), terms(99999, 10, 3)));
    }

    @Test
    @DisplayName("TC-B11 buyer mandate without mandatory scope list is accepted")
    void buyerEmptyScopeList() {
        MandateDTO mandate = buyerMandate();
        MandateDTO noScope = new MandateDTO(
                mandate.mandateId(), mandate.dealId(), PartyRole.BUYER, mandate.version(),
                mandate.currency(), mandate.limitPriceMinorUnits(), mandate.targetPriceMinorUnits(),
                mandate.limitDeliveryDays(), mandate.targetDeliveryDays(),
                mandate.limitRevisions(), mandate.targetRevisions(),
                List.of(), List.of(), mandate.mode(), mandate.maxRounds(), mandate.expiresAt());
        assertScore("1.0000", UtilityCalculator.buyerUtility(noScope, terms(80000, 10, 3)));
    }

    // --------------------------------------------------------------- seller

    @Test
    @DisplayName("TC-S1 seller at target scores 1.0000")
    void sellerAtTargetScoresOne() {
        assertScore("1.0000", UtilityCalculator.sellerUtility(sellerMandate(), terms(100000, 30, 1)));
    }

    @Test
    @DisplayName("TC-S2 seller at limit scores 0.0000")
    void sellerAtLimitScoresZero() {
        assertScore("0.0000", UtilityCalculator.sellerUtility(sellerMandate(), terms(70000, 10, 5)));
    }

    @Test
    @DisplayName("TC-S3 seller midpoint scores 0.5000")
    void sellerMidpointScoresCorrectly() {
        assertScore("0.5000", UtilityCalculator.sellerUtility(sellerMandate(), terms(85000, 20, 3)));
    }

    @Test
    @DisplayName("TC-S4 price below seller floor is a veto")
    void sellerPriceVeto() {
        assertEquals(UtilityCalculator.VETO,
                UtilityCalculator.sellerUtility(sellerMandate(), terms(69999, 30, 1)));
    }

    @Test
    @DisplayName("TC-S5 delivery below seller minimum is a veto")
    void sellerDeliveryVeto() {
        assertEquals(UtilityCalculator.VETO,
                UtilityCalculator.sellerUtility(sellerMandate(), terms(90000, 9, 1)));
    }

    @Test
    @DisplayName("TC-S6 revisions above seller maximum is a veto")
    void sellerRevisionsVeto() {
        assertEquals(UtilityCalculator.VETO,
                UtilityCalculator.sellerUtility(sellerMandate(), terms(90000, 20, 6)));
    }

    @Test
    @DisplayName("TC-S7 seller zero denominator guard returns 1.0000 for the exact acceptable value")
    void sellerZeroDenominatorGuard() {
        MandateDTO mandate = new MandateDTO(
                "m-3", "deal-1", PartyRole.SELLER, 1, "USD",
                90000L, 90000L, 15, 15, 2, 2,
                SCOPE, List.of(), NegotiationMode.ASSISTED, 5, "2026-12-31T23:59:59Z");
        assertScore("1.0000", UtilityCalculator.sellerUtility(mandate, terms(90000, 15, 2)));
    }

    @Test
    @DisplayName("TC-S8 seller components are clamped to [0,1] when terms pass the target")
    void sellerClamping() {
        // uP=40000/30000=1.3333, uD=30/20=1.5000, uR=5/4=1.2500 -> all clamp to 1.0000
        assertScore("1.0000", UtilityCalculator.sellerUtility(sellerMandate(), terms(110000, 40, 0)));
        // Only uP exceeds its target: uP clamps to 1.0000 while uD=10/20=0.5000 and uR=2/4=0.5000.
        // Without the clamp uP would be 1.3333 and the weighted sum 0.99998 -> 1.0000, so this
        // discriminates clamped from unclamped: 0.6*1.0000 + 0.25*0.5000 + 0.15*0.5000 = 0.8000.
        assertScore("0.8000", UtilityCalculator.sellerUtility(sellerMandate(), terms(110000, 20, 3)));
        // Seller asymmetry: the lower bound of each component is reached exactly at the seller's
        // limit (P=Pmin, D=Dmin, R=Rmax) rather than by a clamp action, because going past it is
        // already a hard veto. No seller input reaches a clamp to 0.0000 without vetoing first.
    }

    // ------------------------------------------------- weights & invariants

    @Test
    @DisplayName("TC-W1 default weights sum to exactly 1.00")
    void weightsSumToOne() {
        BigDecimal sum = UtilityCalculator.W_PRICE
                .add(UtilityCalculator.W_DELIVERY)
                .add(UtilityCalculator.W_REVISIONS);
        assertEquals(0, sum.compareTo(BigDecimal.ONE.setScale(2)));
        assertEquals("0.60", UtilityCalculator.W_PRICE.toPlainString());
        assertEquals("0.25", UtilityCalculator.W_DELIVERY.toPlainString());
        assertEquals("0.15", UtilityCalculator.W_REVISIONS.toPlainString());
    }

    @Test
    @DisplayName("TC-W2 weighted result stays within [0,1] for every non-veto input")
    void weightedResultStaysWithinUnitRange() {
        // S15: the FINAL weighted utility score must remain within [0,1] for every non-veto input.
        // Assertions are made on the final public score (the value OfferDTO.callerUtilityScore
        // serializes via formatScore) and not on individual components, so a component-level clamp
        // failure shows up here as an out-of-range final score (e.g. buyer terms(0,0,3) would be
        // 3.5250 without clamping; clamped it is 1.0000).
        for (long price : new long[] {0L, 40000L, 50000L, 80000L, 90000L, 100000L}) {
            for (int delivery : new int[] {1, 10, 20, 30}) {
                for (int revisions : new int[] {0, 1, 3}) {
                    BigDecimal score = UtilityCalculator.buyerUtility(buyerMandate(), terms(price, delivery, revisions));
                    assertTrue(!UtilityCalculator.isVeto(score), "buyer score must not be a veto for this input");
                    assertTrue(score.compareTo(BigDecimal.ZERO) >= 0,
                            "buyer score must be >= 0, was " + score);
                    assertTrue(score.compareTo(BigDecimal.ONE) <= 0,
                            "buyer score must be <= 1, was " + score);
                }
            }
        }
        for (long price : new long[] {70000L, 80000L, 90000L, 100000L, 110000L}) {
            for (int delivery : new int[] {10, 20, 30, 40}) {
                for (int revisions : new int[] {0, 1, 3}) {
                    BigDecimal score = UtilityCalculator.sellerUtility(sellerMandate(), terms(price, delivery, revisions));
                    assertTrue(!UtilityCalculator.isVeto(score), "seller score must not be a veto for this input");
                    assertTrue(score.compareTo(BigDecimal.ZERO) >= 0,
                            "seller score must be >= 0, was " + score);
                    assertTrue(score.compareTo(BigDecimal.ONE) <= 0,
                            "seller score must be <= 1, was " + score);
                }
            }
        }
        // Boundary expectations: exact endpoints and clamped extremes stay within [0,1].
        assertScore("0.0000", UtilityCalculator.buyerUtility(buyerMandate(), terms(100000, 30, 0)));
        assertScore("1.0000", UtilityCalculator.buyerUtility(buyerMandate(), terms(0, 0, 3)));
        assertScore("0.0000", UtilityCalculator.sellerUtility(sellerMandate(), terms(70000, 10, 5)));
        assertScore("1.0000", UtilityCalculator.sellerUtility(sellerMandate(), terms(110000, 40, 0)));
    }

    // ------------------------------------------------------ mandate compliance

    @Test
    @DisplayName("TC-M1 compliant when no veto applies")
    void compliantWhenNoVeto() {
        assertTrue(UtilityCalculator.isMandateCompliant(
                PartyRole.BUYER, buyerMandate(), terms(80000, 10, 3)));
    }

    @Test
    @DisplayName("TC-M2 non-compliant when buyer veto applies")
    void nonCompliantOnVeto() {
        assertFalse(UtilityCalculator.isMandateCompliant(
                PartyRole.BUYER, buyerMandate(), terms(100001, 10, 3)));
    }

    @Test
    @DisplayName("TC-M3 non-compliant when mandatory scope is missing (S = 0)")
    void nonCompliantOnMissingScope() {
        StructuredTermsDTO incomplete =
                new StructuredTermsDTO("Design sprint", List.of("figma"), 80000, "USD", 10, 3, "x");
        assertFalse(UtilityCalculator.isMandateCompliant(
                PartyRole.BUYER, buyerMandate(), incomplete));
    }

    @Test
    @DisplayName("TC-M4 seller with S = 0 scores zero and is non-compliant")
    void sellerMissingScopeScoresZeroAndNonCompliant() {
        StructuredTermsDTO incomplete =
                new StructuredTermsDTO("Design sprint", List.of("figma"), 100000, "USD", 30, 1, "x");
        assertScore("0.0000", UtilityCalculator.sellerUtility(sellerMandate(), incomplete));
        assertFalse(UtilityCalculator.isMandateCompliant(
                PartyRole.SELLER, sellerMandate(), incomplete));
    }

    // ---------------------------------------------------------- convergence

    @Test
    @DisplayName("TC-C1 default convergence threshold is 0.70")
    void convergenceThreshold() {
        assertEquals("0.70", UtilityCalculator.CONVERGENCE_THRESHOLD.toPlainString());
    }

    @Test
    @DisplayName("TC-C2 meets threshold at 0.70 and above")
    void meetsThreshold() {
        assertTrue(UtilityCalculator.meetsConvergenceThreshold(new BigDecimal("0.7000")));
        assertTrue(UtilityCalculator.meetsConvergenceThreshold(new BigDecimal("1.0000")));
        assertFalse(UtilityCalculator.meetsConvergenceThreshold(new BigDecimal("0.6999")));
        assertFalse(UtilityCalculator.meetsConvergenceThreshold(UtilityCalculator.VETO));
    }

    @Test
    @DisplayName("TC-C3 converges when BOTH parties meet the threshold")
    void convergesWhenBothMeetThreshold() {
        assertTrue(UtilityCalculator.isConverged(new BigDecimal("0.7000"), new BigDecimal("0.7000")));
        assertTrue(UtilityCalculator.isConverged(new BigDecimal("0.7500"), new BigDecimal("0.8000")));
        assertTrue(UtilityCalculator.isConverged(new BigDecimal("1.0000"), new BigDecimal("0.7000")));
    }

    @Test
    @DisplayName("TC-C4 does not converge when only one party meets the threshold")
    void doesNotConvergeWhenOnlyOneMeets() {
        assertFalse(UtilityCalculator.isConverged(new BigDecimal("0.7500"), new BigDecimal("0.6999")));
        assertFalse(UtilityCalculator.isConverged(new BigDecimal("0.6999"), new BigDecimal("0.7500")));
    }

    @Test
    @DisplayName("TC-C5 does not converge when neither party meets the threshold")
    void doesNotConvergeWhenNeitherMeets() {
        assertFalse(UtilityCalculator.isConverged(new BigDecimal("0.5000"), new BigDecimal("0.6000")));
        assertFalse(UtilityCalculator.isConverged(new BigDecimal("0.0000"), new BigDecimal("0.0000")));
    }

    @Test
    @DisplayName("TC-C6 does not converge when either party is vetoed")
    void doesNotConvergeWhenEitherVetoed() {
        assertFalse(UtilityCalculator.isConverged(UtilityCalculator.VETO, new BigDecimal("1.0000")));
        assertFalse(UtilityCalculator.isConverged(new BigDecimal("1.0000"), UtilityCalculator.VETO));
        assertFalse(UtilityCalculator.isConverged(UtilityCalculator.VETO, UtilityCalculator.VETO));
    }

    @Test
    @DisplayName("TC-C7 null utilities are rejected")
    void convergedNullRejected() {
        assertThrows(NullPointerException.class,
                () -> UtilityCalculator.isConverged(null, new BigDecimal("0.7000")));
        assertThrows(NullPointerException.class,
                () -> UtilityCalculator.isConverged(new BigDecimal("0.7000"), null));
    }

    @Test
    @DisplayName("TC-C8 two real mandates reach convergence via the threshold path")
    void convergenceViaRealMandates() {
        // S20: run real buyer/seller mandates and real terms through the utility functions, then feed
        // the mandate-derived scores into the convergence predicate. No hand-injected utility values.
        //
        // Pair design (aligned target price = 80000, transparent from the Section 2 formulas):
        //   BUYER:   Pmax=100000, Ptarget=80000, Dmax=30, Dtarget=10, Rmin=0, Rtarget=2
        //   SELLER:  Pmin=60000, Ptarget=80000, Dmin=10, Dtarget=30, Rmax=5,  Rtarget=2
        //
        // Terms x = (P=80000, D=12, R=2, S=1):
        //   U_B = 0.6*(100000-80000)/(100000-80000) + 0.25*(30-12)/(30-10) + 0.15*(2-0)/(2-0) = 0.9750
        //   U_S = 0.6*(80000-60000)/(80000-60000) + 0.25*(12-10)/(30-10) + 0.15*(5-2)/(5-2) = 0.7750
        //   Both >= 0.70 (the convergence threshold) -> converged.
        BigDecimal buyerU = UtilityCalculator.buyerUtility(
                buyerMandate(100000L, 80000L, 30, 10, 0, 2), terms(80000, 12, 2));
        BigDecimal sellerU = UtilityCalculator.sellerUtility(
                sellerMandate(60000L, 80000L, 10, 30, 5, 2), terms(80000, 12, 2));
        assertScore("0.9750", buyerU);
        assertScore("0.7750", sellerU);
        assertTrue(UtilityCalculator.meetsConvergenceThreshold(buyerU));
        assertTrue(UtilityCalculator.meetsConvergenceThreshold(sellerU));
        assertTrue(UtilityCalculator.isConverged(buyerU, sellerU));

        // Nearby negative: same pair, only the buyer drops below the threshold (U_B=0.6750; seller
        // remains at U_S=0.7750 >= 0.70). The two-party AND must therefore return false.
        BigDecimal buyerU2 = UtilityCalculator.buyerUtility(
                buyerMandate(100000L, 80000L, 30, 10, 0, 2), terms(90000, 12, 2));
        BigDecimal sellerU2 = UtilityCalculator.sellerUtility(
                sellerMandate(60000L, 80000L, 10, 30, 5, 2), terms(90000, 12, 2));
        assertScore("0.6750", buyerU2);
        assertScore("0.7750", sellerU2);
        assertTrue(UtilityCalculator.meetsConvergenceThreshold(sellerU2));
        assertFalse(UtilityCalculator.isConverged(buyerU2, sellerU2));
    }

    // ------------------------------------------------------------ formatting

    @Test
    @DisplayName("TC-F1 score serializes at scale 4 for OfferDTO.callerUtilityScore")
    void formatScore() {
        assertEquals("0.7850", UtilityCalculator.formatScore(new BigDecimal("0.785")));
        assertEquals("1.0000", UtilityCalculator.formatScore(BigDecimal.ONE));
    }

    // -------------------------------------------------------- role & nulls

    @Test
    @DisplayName("TC-R1 utilityFor dispatches on party role")
    void utilityForDispatches() {
        assertEquals(UtilityCalculator.buyerUtility(buyerMandate(), terms(80000, 10, 3)),
                UtilityCalculator.utilityFor(PartyRole.BUYER, buyerMandate(), terms(80000, 10, 3)));
        assertEquals(UtilityCalculator.sellerUtility(sellerMandate(), terms(100000, 30, 1)),
                UtilityCalculator.utilityFor(PartyRole.SELLER, sellerMandate(), terms(100000, 30, 1)));
    }

    @Test
    @DisplayName("TC-R2 role mismatch is rejected")
    void roleMismatchRejected() {
        MandateDTO seller = sellerMandate();
        assertThrows(IllegalArgumentException.class,
                () -> UtilityCalculator.buyerUtility(seller, terms(80000, 10, 3)));
        MandateDTO buyer = buyerMandate();
        assertThrows(IllegalArgumentException.class,
                () -> UtilityCalculator.sellerUtility(buyer, terms(80000, 10, 3)));
    }

    @Test
    @DisplayName("TC-N1 null inputs are rejected")
    void nullInputsRejected() {
        assertThrows(NullPointerException.class,
                () -> UtilityCalculator.buyerUtility(null, terms(80000, 10, 3)));
        assertThrows(NullPointerException.class,
                () -> UtilityCalculator.buyerUtility(buyerMandate(), null));
        assertThrows(NullPointerException.class,
                () -> UtilityCalculator.sellerUtility(sellerMandate(), null));
        assertThrows(NullPointerException.class,
                () -> UtilityCalculator.isMandateCompliant(null, buyerMandate(), terms(1, 1, 1)));
    }
}
