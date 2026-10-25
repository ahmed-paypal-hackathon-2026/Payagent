package com.payagent.paypal.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalInvoiceResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status,
        @JsonProperty("detail") Detail detail,
        @JsonProperty("amount") Amount amount,
        @JsonProperty("links") List<Link> links) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detail(
            @JsonProperty("invoice_number") String invoiceNumber,
            @JsonProperty("invoice_date") String invoiceDate,
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("note") String note,
            @JsonProperty("term") String term,
            @JsonProperty("metadata") Metadata metadata) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("payer_view_url") String payerViewUrl,
            @JsonProperty("create_time") String createTime,
            @JsonProperty("last_update_time") String lastUpdateTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("value") String value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(
            @JsonProperty("rel") String rel,
            @JsonProperty("href") String href,
            @JsonProperty("method") String method) {}

    public String getPayerViewUrl() {
        if (detail != null && detail.metadata != null && detail.metadata.payerViewUrl != null) {
            return detail.metadata.payerViewUrl;
        }
        if (links != null) {
            for (Link link : links) {
                if ("payer-view".equalsIgnoreCase(link.rel())) {
                    return link.href();
                }
            }
        }
        return id != null ? "https://www.sandbox.paypal.com/invoice/payerView/details/" + id : null;
    }
}
