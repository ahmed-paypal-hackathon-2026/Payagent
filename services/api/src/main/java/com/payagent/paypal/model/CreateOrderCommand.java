package com.payagent.paypal.model;

public record CreateOrderCommand(
        String dealId,
        String invoiceId,
        String title,
        long amountMinorUnits,
        String currency) {}
