package com.payagent.ai.negotiation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payagent.ai.AiNegotiationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesMajorUnitsIntoIntegerCents() {
        assertEquals(120_050L, MoneyParser.parseMinorUnits("$1,200.50"));
        assertEquals(98_000L, MoneyParser.parseMinorUnits("980"));
        assertEquals(98_000L, MoneyParser.parseMinorUnits("980 USD"));
        assertEquals(1L, MoneyParser.parseMinorUnits("0.01"));
        assertEquals(99L, MoneyParser.parseMinorUnits("€ 0.99"));
    }

    @Test
    void rejectsNonFiniteAndExcessPrecision() {
        assertThrows(AiNegotiationException.class, () -> MoneyParser.parseMinorUnits("NaN"));
        assertThrows(AiNegotiationException.class, () -> MoneyParser.parseMinorUnits("Infinity"));
        assertThrows(AiNegotiationException.class, () -> MoneyParser.parseMinorUnits("1.234"));
        assertThrows(AiNegotiationException.class, () -> MoneyParser.parseMinorUnits("1e3"));
        assertThrows(AiNegotiationException.class, () -> MoneyParser.parseMinorUnits("12abc"));
    }

    @Test
    void rejectsNegativeWhenNonNegativeContractRequired() {
        assertEquals(-500L, MoneyParser.parseMinorUnits("-5.00"));
        assertThrows(AiNegotiationException.class,
                () -> MoneyParser.parseNonNegativeMinorUnits("-5.00"));
    }

    @Test
    void readsJsonIntegersAsMinorUnitsAndTextAsMajorUnits() throws Exception {
        assertEquals(1_250L, MoneyParser.fromJson(mapper.readTree("1250"), "priceMinorUnits"));
        assertEquals(1_250L, MoneyParser.fromJson(mapper.readTree("\"12.50\""), "priceMinorUnits"));
        assertEquals(12L, MoneyParser.fromJson(mapper.readTree("12.0"), "priceMinorUnits"));
        assertThrows(AiNegotiationException.class,
                () -> MoneyParser.fromJson(mapper.readTree("12.5"), "priceMinorUnits"));
        assertThrows(AiNegotiationException.class,
                () -> MoneyParser.fromJson(mapper.readTree("9223372036854775808"), "priceMinorUnits"));
    }

    @Test
    void formatsWithoutBinaryFloatingPoint() {
        assertEquals("1200.50", MoneyParser.formatMajorUnits(120_050L));
        assertEquals("0.01", MoneyParser.formatMajorUnits(1L));
        assertEquals("-1.00", MoneyParser.formatMajorUnits(-100L));
    }
}
