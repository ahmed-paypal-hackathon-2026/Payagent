package com.payagent.ai.negotiation;

import com.payagent.common.contracts.MandateDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ensures LLM-generated prose can never leak the caller's private mandate
 * numbers. If a rationale contains a limit/target value, a mandate field name or
 * an injection marker, the service falls back to a deterministic rationale.
 */
@Component
public class RationaleValidator {

    private static final int MAX_LENGTH = 500;

    public String sanitizeOrNull(String rationale, MandateDTO mandate) {
        return sanitizeOrNull(rationale, mandate, null);
    }

    /**
     * @param publicPriceMinorUnits price that is already visible in the offer;
     *                              quoting it in the rationale is not a leak
     */
    public String sanitizeOrNull(
            String rationale,
            MandateDTO mandate,
            Long publicPriceMinorUnits) {
        if (rationale == null || mandate == null) {
            return null;
        }
        String normalized = rationale.replaceAll("\\s+", " ").strip();
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("limitpriceminorunits")
                || lower.contains("targetpriceminorunits")
                || lower.contains("untrusted_counterparty_data")
                || lower.contains("ignore previous instructions")
                || lower.contains("system:")) {
            return null;
        }
        for (String value : privateNumerals(mandate)) {
            if (!value.isEmpty() && lower.contains(value) && !isPublicPrice(value, publicPriceMinorUnits)) {
                return null;
            }
        }
        return normalized;
    }

    private static boolean isPublicPrice(String value, Long publicPriceMinorUnits) {
        if (publicPriceMinorUnits == null) {
            return false;
        }
        return numerals(publicPriceMinorUnits).contains(value);
    }

    private static List<String> privateNumerals(MandateDTO mandate) {
        long limit = mandate.limitPriceMinorUnits();
        long target = mandate.targetPriceMinorUnits();
        List<String> values = new ArrayList<>();
        values.addAll(numerals(limit));
        values.addAll(numerals(target));
        return values;
    }

    private static List<String> numerals(long minorUnits) {
        BigDecimal value = BigDecimal.valueOf(minorUnits, 2);
        return List.of(
                Long.toString(minorUnits),
                MoneyParser.formatMajorUnits(minorUnits),
                value.stripTrailingZeros().toPlainString(),
                withThousands(minorUnits),
                String.format(Locale.ROOT, "%,.0f", value));
    }

    private static String withThousands(long minorUnits) {
        BigDecimal value = BigDecimal.valueOf(minorUnits, 2);
        return String.format(Locale.ROOT, "%,.2f", value);
    }
}
