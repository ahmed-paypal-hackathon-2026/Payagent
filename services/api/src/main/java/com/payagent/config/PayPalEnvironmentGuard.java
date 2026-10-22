package com.payagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PayPalEnvironmentGuard {

    private final String paypalBaseUrl;

    PayPalEnvironmentGuard(@Value("${payagent.paypal.base-url}") String paypalBaseUrl) {
        this.paypalBaseUrl = paypalBaseUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    void assertSandboxOnly() {
        String normalized = paypalBaseUrl.toLowerCase();
        if (!normalized.contains("sandbox.paypal.com")) {
            throw new IllegalStateException(
                    "PayPal base URL must target Sandbox (api-m.sandbox.paypal.com). Refusing to start.");
        }
    }
}
