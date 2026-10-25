package com.payagent.mandates;

import com.payagent.common.contracts.MandateDTO;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class UtilityCalculator {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal VETO = BigDecimal.valueOf(-1);

    public BigDecimal buyerUtility(MandateDTO mandate, StructuredTermsDTO terms) {
        if (!scopeSatisfied(mandate.mustHaveDeliverables(), terms.deliverables())) return VETO;
        if (terms.priceMinorUnits() > mandate.limitPriceMinorUnits()) return VETO;
        if (terms.deliveryDays() > mandate.limitDeliveryDays()) return VETO;
        if (terms.includedRevisions() < mandate.limitRevisions()) return VETO;

        BigDecimal uP = priceBuyer(mandate.limitPriceMinorUnits(), mandate.targetPriceMinorUnits(), terms.priceMinorUnits());
        BigDecimal uD = deliveryBuyer(mandate.limitDeliveryDays(), mandate.targetDeliveryDays(), terms.deliveryDays());
        BigDecimal uR = revisionsBuyer(mandate.limitRevisions(), mandate.targetRevisions(), terms.includedRevisions());

        BigDecimal weighted = uP.multiply(BigDecimal.valueOf(0.60))
                .add(uD.multiply(BigDecimal.valueOf(0.25)))
                .add(uR.multiply(BigDecimal.valueOf(0.15)));
        return weighted.setScale(SCALE, ROUNDING);
    }

    public BigDecimal sellerUtility(MandateDTO mandate, StructuredTermsDTO terms) {
        if (terms.priceMinorUnits() < mandate.limitPriceMinorUnits()) return VETO;
        if (terms.deliveryDays() < mandate.limitDeliveryDays()) return VETO;
        if (terms.includedRevisions() > mandate.limitRevisions()) return VETO;

        BigDecimal uP = priceSeller(mandate.limitPriceMinorUnits(), mandate.targetPriceMinorUnits(), terms.priceMinorUnits());
        BigDecimal uD = deliverySeller(mandate.limitDeliveryDays(), mandate.targetDeliveryDays(), terms.deliveryDays());
        BigDecimal uR = revisionsSeller(mandate.limitRevisions(), mandate.targetRevisions(), terms.includedRevisions());

        BigDecimal weighted = uP.multiply(BigDecimal.valueOf(0.60))
                .add(uD.multiply(BigDecimal.valueOf(0.25)))
                .add(uR.multiply(BigDecimal.valueOf(0.15)));
        return weighted.setScale(SCALE, ROUNDING);
    }

    private boolean scopeSatisfied(List<String> mustHave, List<String> deliverables) {
        if (mustHave == null || mustHave.isEmpty()) return true;
        if (deliverables == null) return false;
        return deliverables.containsAll(mustHave);
    }

    private BigDecimal priceBuyer(long max, long target, long value) {
        return normalizeInverse(max, target, value);
    }

    private BigDecimal deliveryBuyer(int max, int target, int value) {
        return normalizeInverse(max, target, value);
    }

    private BigDecimal revisionsBuyer(int min, int target, int value) {
        return normalizeDirect(min, target, value);
    }

    private BigDecimal priceSeller(long min, long target, long value) {
        return normalizeDirect(min, target, value);
    }

    private BigDecimal deliverySeller(int min, int target, int value) {
        return normalizeDirect(min, target, value);
    }

    private BigDecimal revisionsSeller(int max, int target, int value) {
        // uR = (Rmax - R) / (Rmax - Rtarget)
        return normalizeInverse(max, target, value);
    }

    private BigDecimal normalizeInverse(long max, long target, long value) {
        return normalizeInverse(BigDecimal.valueOf(max), BigDecimal.valueOf(target), BigDecimal.valueOf(value));
    }

    private BigDecimal normalizeInverse(int max, int target, int value) {
        return normalizeInverse(BigDecimal.valueOf(max), BigDecimal.valueOf(target), BigDecimal.valueOf(value));
    }

    private BigDecimal normalizeInverse(BigDecimal max, BigDecimal target, BigDecimal value) {
        if (max.compareTo(target) == 0) {
            return value.compareTo(max) == 0 ? ONE : ZERO;
        }
        BigDecimal numerator = max.subtract(value);
        BigDecimal denominator = max.subtract(target);
        BigDecimal raw = numerator.divide(denominator, SCALE + 4, ROUNDING);
        return clamp(raw);
    }

    private BigDecimal normalizeDirect(long min, long target, long value) {
        return normalizeDirect(BigDecimal.valueOf(min), BigDecimal.valueOf(target), BigDecimal.valueOf(value));
    }

    private BigDecimal normalizeDirect(int min, int target, int value) {
        return normalizeDirect(BigDecimal.valueOf(min), BigDecimal.valueOf(target), BigDecimal.valueOf(value));
    }

    private BigDecimal normalizeDirect(BigDecimal min, BigDecimal target, BigDecimal value) {
        if (target.compareTo(min) == 0) {
            return value.compareTo(target) == 0 ? ONE : ZERO;
        }
        BigDecimal numerator = value.subtract(min);
        BigDecimal denominator = target.subtract(min);
        BigDecimal raw = numerator.divide(denominator, SCALE + 4, ROUNDING);
        return clamp(raw);
    }

    private BigDecimal clamp(BigDecimal v) {
        if (v.compareTo(ZERO) < 0) return ZERO;
        if (v.compareTo(ONE) > 0) return ONE;
        return v;
    }
}
