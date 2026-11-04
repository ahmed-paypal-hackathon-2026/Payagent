package com.payagent.paypal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayPalMoneyConverterTest {

    @Test
    void convertsMinorUnitsToDecimalStringForUsd() {
        assertThat(PayPalMoneyConverter.toDecimalString(98000L, "USD")).isEqualTo("980.00");
        assertThat(PayPalMoneyConverter.toDecimalString(1L, "USD")).isEqualTo("0.01");
        assertThat(PayPalMoneyConverter.toDecimalString(0L, "USD")).isEqualTo("0.00");
        assertThat(PayPalMoneyConverter.toDecimalString(105L, "USD")).isEqualTo("1.05");
        assertThat(PayPalMoneyConverter.toDecimalString(12345678L, "USD")).isEqualTo("123456.78");
    }

    @Test
    void convertsDecimalStringToMinorUnitsForUsd() {
        assertThat(PayPalMoneyConverter.toMinorUnits("980.00", "USD")).isEqualTo(98000L);
        assertThat(PayPalMoneyConverter.toMinorUnits("0.01", "USD")).isEqualTo(1L);
        assertThat(PayPalMoneyConverter.toMinorUnits("0.00", "USD")).isEqualTo(0L);
        assertThat(PayPalMoneyConverter.toMinorUnits("1.05", "USD")).isEqualTo(105L);
        assertThat(PayPalMoneyConverter.toMinorUnits("123456.78", "USD")).isEqualTo(12345678L);
    }

    @Test
    void handlesZeroDecimalCurrencies() {
        assertThat(PayPalMoneyConverter.toDecimalString(5000L, "JPY")).isEqualTo("5000");
        assertThat(PayPalMoneyConverter.toMinorUnits("5000", "JPY")).isEqualTo(5000L);
    }
}
