package com.payagent.mandates;

import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.PartyRole;
import com.payagent.common.contracts.StructuredTermsDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * Deterministic multi-attribute utility scorer.
 *
 * <p>Source of truth: {@code plan v2.docx} Section 2 (extract in {@code docs/_plan_extract.txt}),
 * "Deterministic Mathematical Model for Bounded Negotiation":
 *
 * <pre>
 *   Each offer is a vector x = (P, D, R, S):
 *     P &gt; Z+  price in minor currency units (cents)
 *     D &gt; Z+  delivery time in calendar days
 *     R &gt; Z+0 included revision count
 *     S &gt; {0,1} boolean inclusion of all mandatory scope deliverables (S=1 required; if S=0, U(x)=0)
 *
 *   Weights: wP + wD + wR = 1.00, default wP=0.60, wD=0.25, wR=0.15
 *   BigDecimal scale 4, RoundingMode.HALF_UP. Result range [0,1]; veto returns -1.
 * </pre>
 *
 * <p>Mandate parameter mapping (see Section 2.1 / 2.2):
 * <ul>
 *   <li>BUYER:  PmaxB=limitPriceMinorUnits, PtargetB=targetPriceMinorUnits,
 *       DmaxB=limitDeliveryDays, DtargetB=targetDeliveryDays,
 *       RminB=limitRevisions, RtargetB=targetRevisions</li>
 *   <li>SELLER: PminS=limitPriceMinorUnits, PtargetS=targetPriceMinorUnits,
 *       DminS=limitDeliveryDays, DtargetS=targetDeliveryDays,
 *       RmaxS=limitRevisions, RtargetS=targetRevisions</li>
 * </ul>
 *
 * <p>Convergence rule (Section 2.2): negotiation converges when both
 * U_B(x) &gt;= theta_B and U_S(x) &gt;= theta_S (default 0.70).
 */
@Service
public final class UtilityCalculator {

    /** Default price weight (plan Section 2.1). */
    public static final BigDecimal W_PRICE = new BigDecimal("0.60");

    /** Default delivery weight (plan Section 2.1). */
    public static final BigDecimal W_DELIVERY = new BigDecimal("0.25");

    /** Default revisions weight (plan Section 2.1). */
    public static final BigDecimal W_REVISIONS = new BigDecimal("0.15");

    /** Default convergence threshold for both parties (plan Section 2.2). */
    public static final BigDecimal CONVERGENCE_THRESHOLD = new BigDecimal("0.70");

    /** Sentinel returned when a hard veto condition is met. */
    public static final BigDecimal VETO = new BigDecimal("-1");

    private static final int SCALE = 4;
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(SCALE);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    public UtilityCalculator() {
    }

    // ---------------------------------------------------------------- buyer

    /**
     * Computes U_B(x) for a buyer mandate.
     *
     * @return utility in [0,1] at scale 4, or {@link #VETO} ({@code -1}) on hard veto
     */
    public static BigDecimal buyerUtility(MandateDTO mandate, StructuredTermsDTO terms) {
        Objects.requireNonNull(mandate, "mandate must not be null");
        Objects.requireNonNull(terms, "terms must not be null");
        requireRole(mandate, PartyRole.BUYER);
        requireNonNegative(mandate.limitPriceMinorUnits(), "limitPriceMinorUnits");
        requireNonNegative(mandate.targetPriceMinorUnits(), "targetPriceMinorUnits");

        // Hard Veto Check: P > PmaxB or D > DmaxB or R < RminB or S = 0
        boolean veto = terms.priceMinorUnits() > mandate.limitPriceMinorUnits()
                || terms.deliveryDays() > mandate.limitDeliveryDays()
                || terms.includedRevisions() < mandate.limitRevisions()
                || !includesAllMandatoryScope(mandate, terms);
        if (veto) {
            return VETO;
        }

        BigDecimal uPrice = component(
                of(mandate.limitPriceMinorUnits() - terms.priceMinorUnits()),
                of(mandate.limitPriceMinorUnits() - mandate.targetPriceMinorUnits()));
        BigDecimal uDelivery = component(
                of(mandate.limitDeliveryDays() - terms.deliveryDays()),
                of(mandate.limitDeliveryDays() - mandate.targetDeliveryDays()));
        BigDecimal uRevisions = component(
                of(terms.includedRevisions() - mandate.limitRevisions()),
                of(mandate.targetRevisions() - mandate.limitRevisions()));

        return weightedTotal(uPrice, uDelivery, uRevisions);
    }

    // --------------------------------------------------------------- seller

    /**
     * Computes U_S(x) for a seller mandate.
     *
     * @return utility in [0,1] at scale 4, or {@link #VETO} ({@code -1}) on hard veto
     */
    public static BigDecimal sellerUtility(MandateDTO mandate, StructuredTermsDTO terms) {
        Objects.requireNonNull(mandate, "mandate must not be null");
        Objects.requireNonNull(terms, "terms must not be null");
        requireRole(mandate, PartyRole.SELLER);
        requireNonNegative(mandate.limitPriceMinorUnits(), "limitPriceMinorUnits");
        requireNonNegative(mandate.targetPriceMinorUnits(), "targetPriceMinorUnits");

        // Hard Veto Check: P < PminS or D < DminS or R > RmaxS
        boolean veto = terms.priceMinorUnits() < mandate.limitPriceMinorUnits()
                || terms.deliveryDays() < mandate.limitDeliveryDays()
                || terms.includedRevisions() > mandate.limitRevisions();
        if (veto) {
            return VETO;
        }

        BigDecimal uPrice = component(
                of(terms.priceMinorUnits() - mandate.limitPriceMinorUnits()),
                of(mandate.targetPriceMinorUnits() - mandate.limitPriceMinorUnits()));
        BigDecimal uDelivery = component(
                of(terms.deliveryDays() - mandate.limitDeliveryDays()),
                of(mandate.targetDeliveryDays() - mandate.limitDeliveryDays()));
        BigDecimal uRevisions = component(
                of(mandate.limitRevisions() - terms.includedRevisions()),
                of(mandate.limitRevisions() - mandate.targetRevisions()));

        BigDecimal total = weightedTotal(uPrice, uDelivery, uRevisions);

        // Section 2 general rule: if S = 0 the offer carries zero utility. Buyer treats this
        // as a hard veto; the seller veto list in Section 2.2 does not, so score it as 0.
        if (!includesAllMandatoryScope(mandate, terms)) {
            return ZERO;
        }
        return total;
    }

    // ------------------------------------------------------- helper methods

    /**
     * @return true when no hard veto condition is met for the given party.
     */
    public static boolean isMandateCompliant(PartyRole role, MandateDTO mandate, StructuredTermsDTO terms) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(mandate, "mandate must not be null");
        Objects.requireNonNull(terms, "terms must not be null");

        BigDecimal utility = utilityFor(role, mandate, terms);
        if (isVeto(utility)) {
            return false;
        }
        // S = 1 is required by Section 2 even though the seller veto list omits it.
        return includesAllMandatoryScope(mandate, terms);
    }

    /**
     * Dispatches on party role.
     */
    public static BigDecimal utilityFor(PartyRole role, MandateDTO mandate, StructuredTermsDTO terms) {
        Objects.requireNonNull(role, "role must not be null");
        return switch (role) {
            case BUYER -> buyerUtility(mandate, terms);
            case SELLER -> sellerUtility(mandate, terms);
        };
    }

    /**
     * @return true when the utility meets the default convergence threshold (plan Section 2.2).
     */
    public static boolean meetsConvergenceThreshold(BigDecimal utility) {
        Objects.requireNonNull(utility, "utility must not be null");
        if (isVeto(utility)) {
            return false;
        }
        return utility.compareTo(CONVERGENCE_THRESHOLD) >= 0;
    }

    /**
     * Applies the plan Section 2.2 convergence rule on its threshold path: an offer
     * converges only when <em>both</em> parties meet the default threshold.
     *
     * <pre>
     *   U_B(x) &gt;= theta_B (0.70)  AND  U_S(x) &gt;= theta_S (0.70)
     * </pre>
     *
     * <p>This method implements only that threshold path.
     *
     * <p><strong>Not implemented here:</strong> the source's second convergence path, "or when
     * either party clicks 'Propose Final Candidate'". That is a user action, and no code in this
     * repository currently implements it or any deal state machine at all.
     *
     * <p><strong>Unresolved source inconsistency:</strong> plan v2.docx Section 2.2 states that
     * convergence transitions the negotiation to {@code AGREEMENT_CANDIDATE}, but that literal
     * appears only in the Section 2.2 narrative — it is absent from the canonical {@code DealStatus}
     * union in Section 3.1 and from {@code com.payagent.common.contracts.DealStatus}, which instead
     * defines {@code TERMS_PENDING_ACCEPTANCE}. This contradiction is not resolved here; see the
     * issues/decisions log.
     *
     * @param buyerUtility  U_B(x) for the buyer mandate; may be {@link #VETO}
     * @param sellerUtility U_S(x) for the seller mandate; may be {@link #VETO}
     * @return {@code true} iff neither utility is a veto and both are &gt;= the threshold
     */
    public static boolean isConverged(BigDecimal buyerUtility, BigDecimal sellerUtility) {
        Objects.requireNonNull(buyerUtility, "buyerUtility must not be null");
        Objects.requireNonNull(sellerUtility, "sellerUtility must not be null");
        if (isVeto(buyerUtility) || isVeto(sellerUtility)) {
            return false;
        }
        return meetsConvergenceThreshold(buyerUtility)
                && meetsConvergenceThreshold(sellerUtility);
    }

    /**
     * @return true when the value is the veto sentinel.
     */
    public static boolean isVeto(BigDecimal utility) {
        return utility != null && utility.compareTo(VETO) == 0;
    }

    /**
     * Serializes a utility score for {@code OfferDTO.callerUtilityScore} (plan Section 3.1,
     * e.g. {@code "0.7850"}).
     */
    public static String formatScore(BigDecimal utility) {
        Objects.requireNonNull(utility, "utility must not be null");
        return utility.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal weightedTotal(BigDecimal uPrice, BigDecimal uDelivery, BigDecimal uRevisions) {
        BigDecimal total = W_PRICE.multiply(uPrice)
                .add(W_DELIVERY.multiply(uDelivery))
                .add(W_REVISIONS.multiply(uRevisions));
        return total.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Computes one clamped component score.
     *
     * <p>A zero denominator means target == limit, so only that exact value is acceptable;
     * any other value has already been rejected by the veto check.
     */
    private static BigDecimal component(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return ONE;
        }
        BigDecimal ratio = numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
        return clamp(ratio);
    }

    private static BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(ONE) > 0) {
            return ONE;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return ZERO;
        }
        return value;
    }

    /**
     * Mandatory-scope check: S = 1 requires that every entry of
     * {@code mandate.mustHaveDeliverables()} appears verbatim in {@code terms.deliverables()}.
     */
    private static boolean includesAllMandatoryScope(MandateDTO mandate, StructuredTermsDTO terms) {
        List<String> required = mandate.mustHaveDeliverables();
        if (required == null || required.isEmpty()) {
            return true;
        }
        List<String> delivered = terms.deliverables();
        if (delivered == null) {
            return false;
        }
        return delivered.containsAll(required);
    }

    private static BigDecimal of(long value) {
        return BigDecimal.valueOf(value);
    }

    private static void requireRole(MandateDTO mandate, PartyRole expected) {
        if (mandate.partyRole() == null) {
            throw new IllegalArgumentException("mandate.partyRole must not be null");
        }
        if (mandate.partyRole() != expected) {
            throw new IllegalArgumentException(
                    "mandate.partyRole must be " + expected + " but was " + mandate.partyRole());
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
    }
}
