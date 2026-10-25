package com.payagent.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PayPalEnvironmentGuardTest {

    @Test
    void acceptsSandboxBaseUrl() {
        PayPalEnvironmentGuard guard = new PayPalEnvironmentGuard("https://api-m.sandbox.paypal.com");

        assertThatCode(guard::assertSandboxOnly).doesNotThrowAnyException();
    }

    @Test
    void acceptsSandboxBaseUrlCaseInsensitively() {
        PayPalEnvironmentGuard guard = new PayPalEnvironmentGuard("https://API-M.SANDBOX.PAYPAL.COM");

        assertThatCode(guard::assertSandboxOnly).doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionBaseUrl() {
        PayPalEnvironmentGuard guard = new PayPalEnvironmentGuard("https://api-m.paypal.com");

        assertThatThrownBy(guard::assertSandboxOnly)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must target Sandbox");
    }

    @Test
    void rejectsNonPayPalBaseUrl() {
        PayPalEnvironmentGuard guard = new PayPalEnvironmentGuard("https://example.com");

        assertThatThrownBy(guard::assertSandboxOnly)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must target Sandbox");
    }
}
