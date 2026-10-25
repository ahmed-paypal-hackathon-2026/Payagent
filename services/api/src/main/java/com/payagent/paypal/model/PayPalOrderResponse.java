package com.payagent.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalOrderResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("intent") String intent,
        @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits,
        @JsonProperty("links") List<Link> links) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PurchaseUnit(
            @JsonProperty("reference_id") String referenceId,
            @JsonProperty("invoice_id") String invoiceId,
            @JsonProperty("amount") Amount amount,
            @JsonProperty("payments") Payments payments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("value") String value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payments(
            @JsonProperty("authorizations") List<AuthorizationDetail> authorizations,
            @JsonProperty("captures") List<CaptureDetail> captures) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorizationDetail(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("amount") Amount amount,
            @JsonProperty("expiration_time") String expirationTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaptureDetail(
            @JsonProperty("id") String id,
            @JsonProperty("status") String status,
            @JsonProperty("amount") Amount amount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(
            @JsonProperty("rel") String rel,
            @JsonProperty("href") String href,
            @JsonProperty("method") String method) {}

    public String getApproveUrl() {
        if (links == null) return null;
        for (Link link : links) {
            if ("approve".equalsIgnoreCase(link.rel()) || "payer-action".equalsIgnoreCase(link.rel())) {
                return link.href();
            }
        }
        return null;
    }

    public AuthorizationDetail getFirstAuthorization() {
        if (purchaseUnits != null && !purchaseUnits.isEmpty()) {
            PurchaseUnit unit = purchaseUnits.get(0);
            if (unit.payments() != null && unit.payments().authorizations() != null && !unit.payments().authorizations().isEmpty()) {
                return unit.payments().authorizations().get(0);
            }
        }
        return null;
    }
}
