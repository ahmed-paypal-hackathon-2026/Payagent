package com.payagent.ai.negotiation;

import com.fasterxml.jackson.databind.JsonNode;
import com.payagent.ai.AiNegotiationException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converts textual or JSON money values into integer minor units.
 *
 * <p>All financial values in Payagent are {@code long} cents. This class never
 * uses binary floating point: parsing goes through {@link BigDecimal} and then
 * {@link BigDecimal#longValueExact()}.</p>
 */
public final class MoneyParser {

    private static final Pattern ISO_CURRENCY = Pattern.compile("^[A-Za-z]{3}$");
    private static final int MAX_SCALE = 2;

    private MoneyParser() {}

    /**
     * Parses values such as {@code "980"}, {@code "$1,200.50"}, {@code "1200 USD"}
     * and {@code "€ 99.9"} into minor units.
     */
    public static long parseMinorUnits(String raw) {
        if (raw == null) {
            throw invalid("price is missing");
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            throw invalid("price is empty");
        }

        text = text.replace(',', ' ').trim();
        StringBuilder numeric = new StringBuilder();
        boolean decimalSeen = false;
        boolean signAllowed = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '+' || c == '-') {
                if (!signAllowed || numeric.length() > 0) {
                    throw invalid("price contains an unexpected sign");
                }
                numeric.append(c);
                signAllowed = false;
            } else if (c == '.') {
                if (decimalSeen) {
                    throw invalid("price contains more than one decimal point");
                }
                numeric.append(c);
                decimalSeen = true;
                signAllowed = false;
            } else if (Character.isDigit(c)) {
                numeric.append(c);
                signAllowed = false;
            } else if (Character.isWhitespace(c) || c == '$' || c == '€' || c == '£' || c == '¥') {
                continue;
            } else if (Character.isLetter(c)) {
                String word = readWord(text, i);
                if (!isCurrencyCode(word)) {
                    throw invalid("price contains unsupported text");
                }
                i += word.length() - 1;
            } else {
                throw invalid("price contains unsupported characters");
            }
        }

        String number = numeric.toString();
        if (number.isEmpty() || number.equals("+") || number.equals("-") || number.equals(".")) {
            throw invalid("price does not contain a number");
        }
        if (number.startsWith("+")) {
            number = number.substring(1);
        }

        BigDecimal value;
        try {
            value = new BigDecimal(number);
        } catch (NumberFormatException ex) {
            throw invalid("price is not a finite decimal number");
        }
        if (!value.toPlainString().matches(".*\\d.*")) {
            throw invalid("price does not contain a number");
        }
        if (value.stripTrailingZeros().scale() > MAX_SCALE) {
            throw invalid("price has more than two fractional digits");
        }
        try {
            return value.movePointRight(MAX_SCALE).longValueExact();
        } catch (ArithmeticException ex) {
            throw invalid("price is outside the supported range");
        }
    }

    public static long parseNonNegativeMinorUnits(String raw) {
        long value = parseMinorUnits(raw);
        if (value < 0) {
            throw invalid("price must not be negative");
        }
        return value;
    }

    /**
     * Reads a JSON numeric/text node. JSON numbers are treated as minor units when
     * integral, otherwise rejected; text is parsed with {@link #parseMinorUnits(String)}.
     */
    public static long fromJson(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            throw invalid(fieldName + " is missing");
        }
        if (node.isIntegralNumber()) {
            try {
                return node.bigIntegerValue().longValueExact();
            } catch (ArithmeticException ex) {
                throw invalid(fieldName + " is outside the supported range");
            }
        }
        if (node.isTextual()) {
            return parseMinorUnits(node.textValue());
        }
        if (node.isFloatingPointNumber()) {
            BigDecimal value = node.decimalValue();
            if (value.stripTrailingZeros().scale() > 0) {
                throw invalid(fieldName + " must be integer minor units");
            }
            try {
                return value.longValueExact();
            } catch (ArithmeticException ex) {
                throw invalid(fieldName + " is outside the supported range");
            }
        }
        throw invalid(fieldName + " must be an integer amount");
    }

    public static String formatMajorUnits(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2).toPlainString();
    }

    private static boolean isCurrencyCode(String word) {
        if (!ISO_CURRENCY.matcher(word).matches()) {
            return false;
        }
        try {
            Currency.getInstance(word);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String readWord(String text, int start) {
        int end = start;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end).toUpperCase(Locale.ROOT);
    }

    private static AiNegotiationException invalid(String message) {
        return new AiNegotiationException(AiNegotiationException.Code.INVALID_TERMS, message);
    }
}
