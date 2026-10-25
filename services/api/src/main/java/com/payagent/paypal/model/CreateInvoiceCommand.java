package com.payagent.paypal.model;

import java.util.List;

public record CreateInvoiceCommand(
        String dealId,
        String sellerEmail,
        String buyerEmail,
        String title,
        List<String> deliverables,
        long amountMinorUnits,
        String currency,
        String termsHashSha256) {}
