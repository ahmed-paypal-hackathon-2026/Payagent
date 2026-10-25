package com.payagent.paypal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Set;

public final class PayPalMoneyConverter {

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of("JPY", "KRW", "VND", "CLP", "PYG", "UGX");

    private PayPalMoneyConverter() {}

    public static String toDecimalString(long minorUnits, String currencyCode) {
        int decimals = getDecimalPlaces(currencyCode);
        if (decimals == 0) {
            return String.valueOf(minorUnits);
        }
        return BigDecimal.valueOf(minorUnits)
                .movePointLeft(decimals)
                .setScale(decimals, RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    public static long toMinorUnits(String decimalString, String currencyCode) {
        if (decimalString == null || decimalString.isBlank()) {
            return 0L;
        }
        int decimals = getDecimalPlaces(currencyCode);
        BigDecimal decimal = new BigDecimal(decimalString.trim());
        if (decimals == 0) {
            return decimal.longValueExact();
        }
        return decimal.movePointRight(decimals).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private static int getDecimalPlaces(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return 2;
        }
        String normalized = currencyCode.trim().toUpperCase();
        if (ZERO_DECIMAL_CURRENCIES.contains(normalized)) {
            return 0;
        }
        try {
            Currency currency = Currency.getInstance(normalized);
            int fractionDigits = currency.getDefaultFractionDigits();
            return fractionDigits >= 0 ? fractionDigits : 2;
        } catch (IllegalArgumentException e) {
            return 2;
        }
    }
}
