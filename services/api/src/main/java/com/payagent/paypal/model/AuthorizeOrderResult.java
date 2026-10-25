package com.payagent.paypal.model;

public record AuthorizeOrderResult(
        String orderId,
        String authorizationId,
        String status,
        String expirationTime) {}
