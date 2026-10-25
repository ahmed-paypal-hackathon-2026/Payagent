package com.payagent.ai.intake;

import com.payagent.ai.negotiation.MoneyParser;
import com.payagent.common.contracts.StructuredTermsDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic extractor used when no LLM is configured, when the LLM times
 * out, and as a cross-check against LLM hallucinations. It never invents a
 * confident value: missing fields are flagged in the result.
 */
@Component
public class DeterministicIntakeExtractor {

    private static final Pattern SYMBOL_K_PRICE = Pattern.compile(
            "(?i)(?:\\$|€|£)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*k\\b");
    private static final Pattern SYMBOL_PRICE = Pattern.compile(
            "(?i)(?:\\$|€|£)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern CURRENCY_SUFFIX_PRICE = Pattern.compile(
            "(?i)\\b([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*"
                    + "(?:usd|eur|gbp|egp|cad|aud|dollars?|euros?|pounds?)\\b");
    private static final Pattern LABELLED_PRICE = Pattern.compile(
            "(?i)\\b(?:budget|price|cost|fee|compensation|payment|pay)\\b"
                    + "[^\\n0-9]{0,24}([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern THOUSANDS_PRICE = Pattern.compile(
            "(?i)\\b(?:budget|price|cost|fee)\\b[^\\n]{0,24}?([0-9]+(?:\\.[0-9]+)?)\\s*k\\b");
    private static final Pattern DELIVERY = Pattern.compile(
            "(?i)\\b([0-9]{1,4})\\s*(?:calendar\\s+)?(days?|weeks?)\\b");
    private static final Pattern DELIVERY_DAYS = Pattern.compile(
            "(?i)\\b([0-9]{1,3})[- ]day\\b");
    private static final Pattern REVISIONS = Pattern.compile(
            "(?i)\\b([0-9]{1,3})\\s*(?:rounds?\\s+of\\s+)?(?:included\\s+)?revisions?\\b");
    private static final Pattern BULLET = Pattern.compile(
            "^\\s*(?:[-*•]|\\d+[.)])\\s+(.+)$");
    private static final Pattern ACCEPTANCE = Pattern.compile(
            "(?i)^\\s*(?:acceptance(?:\\s+criteria)?|criteria|definition\\s+of\\s+done|done\\s+when)"
                    + "\\s*[:\\-]?\\s*(.+)$");
    private static final Pattern CURRENCY_IN_TEXT = Pattern.compile(
            "(?i)\\b(USD|EUR|GBP|EGP|CAD|AUD)\\b");

    public IntakeExtractionResult extract(String rawText, String requestedCurrency) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalArgumentException("rawText must not be blank");
        }
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n').strip();
        List<String> warnings = new ArrayList<>();

        String currency = normalizeCurrency(requestedCurrency);
        if (currency == null) {
            Matcher currencyMatcher = CURRENCY_IN_TEXT.matcher(text);
            if (currencyMatcher.find()) {
                currency = currencyMatcher.group(1).toUpperCase(Locale.ROOT);
            } else if (text.contains("€")) {
                currency = "EUR";
            } else if (text.contains("£")) {
                currency = "GBP";
            } else {
                currency = "USD";
            }
            warnings.add("CURRENCY_DEFAULTED");
        }

        Long price = extractPrice(text, warnings);
        Integer deliveryDays = extractDeliveryDays(text, warnings);
        int revisions = extractRevisions(text, warnings);
        String title = extractTitle(text);
        List<String> deliverables = extractDeliverables(text, title);
        String acceptance = extractAcceptance(text);

        boolean complete = price != null && deliveryDays != null;
        if (price == null) {
            warnings.add("PRICE_NOT_FOUND: user confirmation required");
        }
        if (deliveryDays == null) {
            warnings.add("DELIVERY_DAYS_NOT_FOUND: user confirmation required");
        }

        StructuredTermsDTO terms = new StructuredTermsDTO(
                title,
                deliverables,
                price == null ? 0L : price,
                currency,
                deliveryDays == null ? 1 : deliveryDays,
                revisions,
                acceptance);
        return new IntakeExtractionResult(
                terms, IntakeExtractionResult.Source.DETERMINISTIC, complete, warnings, 0L);
    }

    private static Long extractPrice(String text, List<String> warnings) {
        Matcher matcher = SYMBOL_K_PRICE.matcher(text);
        if (matcher.find()) {
            return parsePriceInThousands(matcher.group(1), warnings);
        }
        matcher = THOUSANDS_PRICE.matcher(text);
        if (matcher.find()) {
            return parsePriceInThousands(matcher.group(1), warnings);
        }
        matcher = SYMBOL_PRICE.matcher(text);
        if (matcher.find()) {
            return parsePrice(matcher.group(1), warnings);
        }
        matcher = CURRENCY_SUFFIX_PRICE.matcher(text);
        if (matcher.find()) {
            return parsePrice(matcher.group(1), warnings);
        }
        matcher = LABELLED_PRICE.matcher(text);
        if (matcher.find()) {
            return parsePrice(matcher.group(1), warnings);
        }
        return null;
    }

    private static Long parsePriceInThousands(String value, List<String> warnings) {
        try {
            return Math.multiplyExact(MoneyParser.parseNonNegativeMinorUnits(value), 1000L);
        } catch (RuntimeException ex) {
            warnings.add("PRICE_PARSE_FAILED");
            return null;
        }
    }

    private static Long parsePrice(String value, List<String> warnings) {
        try {
            return MoneyParser.parseNonNegativeMinorUnits(value);
        } catch (RuntimeException ex) {
            warnings.add("PRICE_PARSE_FAILED");
            return null;
        }
    }

    private static Integer extractDeliveryDays(String text, List<String> warnings) {
        Matcher matcher = DELIVERY.matcher(text);
        if (matcher.find()) {
            try {
                int amount = Integer.parseInt(matcher.group(1));
                boolean weeks = matcher.group(2).toLowerCase(Locale.ROOT).startsWith("week");
                long days = weeks ? amount * 7L : amount;
                if (days >= 1 && days <= 3650) {
                    return (int) days;
                }
                warnings.add("DELIVERY_DAYS_OUT_OF_RANGE");
                return null;
            } catch (NumberFormatException ex) {
                warnings.add("DELIVERY_DAYS_PARSE_FAILED");
                return null;
            }
        }
        matcher = DELIVERY_DAYS.matcher(text);
        if (matcher.find()) {
            int days = Integer.parseInt(matcher.group(1));
            return days >= 1 && days <= 3650 ? days : null;
        }
        return null;
    }

    private static int extractRevisions(String text, List<String> warnings) {
        Matcher matcher = REVISIONS.matcher(text);
        if (matcher.find()) {
            try {
                return Math.max(0, Math.min(100, Integer.parseInt(matcher.group(1))));
            } catch (NumberFormatException ex) {
                warnings.add("REVISIONS_PARSE_FAILED");
                return 0;
            }
        }
        warnings.add("REVISIONS_NOT_FOUND: defaulted to 0");
        return 0;
    }

    private static String extractTitle(String text) {
        for (String line : text.split("\n")) {
            String candidate = line.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.startsWith("budget") || lower.startsWith("price")
                    || lower.startsWith("delivery") || lower.startsWith("acceptance")) {
                continue;
            }
            candidate = candidate.replaceFirst("^#{1,6}\\s*", "");
            candidate = candidate.replaceFirst("^(?i)(project|title)\\s*[:\\-]\\s*", "");
            candidate = candidate.replaceFirst("^[-*•]\\s*", "");
            if (candidate.isBlank()) {
                continue;
            }
            return truncate(candidate, 160);
        }
        return "Untitled project";
    }

    private static List<String> extractDeliverables(String text, String title) {
        Set<String> values = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            Matcher matcher = BULLET.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String value = matcher.group(1).strip();
            if (isMetadataLine(value)) {
                continue;
            }
            values.add(truncate(value, 300));
            if (values.size() >= 20) {
                break;
            }
        }
        if (values.isEmpty()) {
            for (String sentence : text.split("(?<=[.;])\\s+|\\n")) {
                String lower = sentence.toLowerCase(Locale.ROOT);
                if ((lower.contains("deliverable") || lower.contains("include")
                        || lower.contains("build") || lower.contains("create")
                        || lower.contains("design") || lower.contains("develop")
                        || lower.contains("need"))) {
                    String cleaned = sentence.strip()
                            .replaceFirst("(?i)^(?:i|we)\\s+(?:need|want|would like)\\s+", "")
                            .replaceFirst("(?i)^please\\s+", "")
                            .replaceFirst("[.;]+$", "");
                    if (cleaned.length() >= 4 && !isMetadataLine(cleaned)) {
                        values.add(truncate(cleaned, 300));
                    }
                }
                if (values.size() >= 10) {
                    break;
                }
            }
        }
        if (values.isEmpty()) {
            values.add(title);
        }
        return List.copyOf(values);
    }

    private static String extractAcceptance(String text) {
        for (String line : text.split("\n")) {
            Matcher matcher = ACCEPTANCE.matcher(line);
            if (matcher.matches() && !matcher.group(1).isBlank()) {
                return truncate(matcher.group(1).strip(), 2000);
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int index = lower.indexOf("acceptance criteria");
        if (index >= 0) {
            String tail = text.substring(index + "acceptance criteria".length())
                    .replaceFirst("^[:\\-\\s]+", "")
                    .strip();
            if (!tail.isBlank()) {
                return truncate(tail, 2000);
            }
        }
        return "The buyer confirms that all listed deliverables are complete and match the agreed scope.";
    }

    private static boolean isMetadataLine(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("budget") || lower.startsWith("price")
                || lower.startsWith("delivery") || lower.startsWith("deadline")
                || lower.startsWith("revision") || lower.startsWith("acceptance")
                || lower.startsWith("currency");
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null) {
            return null;
        }
        String normalized = currency.strip().toUpperCase(Locale.ROOT);
        return normalized.length() == 3 && normalized.chars().allMatch(Character::isLetter)
                ? normalized
                : null;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
