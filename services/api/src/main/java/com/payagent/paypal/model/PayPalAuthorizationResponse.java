package com.payagent.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalAuthorizationResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("amount") Amount amount,
        @JsonProperty("expiration_time") String expirationTime) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("value") String value) {}
}
