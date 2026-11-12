package com.payagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class DatasourceUrlConstructionTest {

    private static final String PLACEHOLDER_URL =
            "jdbc:postgresql://${PAYAGENT_DB_HOST:localhost}:${PAYAGENT_DB_PORT:5432}/${PAYAGENT_DB_NAME:payagent}";

    @Test
    void datasourceUrlUsesHostPortAndDatabaseSeparately() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "PAYAGENT_DB_HOST", "pg.internal.example",
                "PAYAGENT_DB_PORT", "6543",
                "PAYAGENT_DB_NAME", "billing_db")));

        String resolved = env.resolveRequiredPlaceholders(PLACEHOLDER_URL);

        assertThat(resolved).isEqualTo("jdbc:postgresql://pg.internal.example:6543/billing_db");
        assertThat(resolved).doesNotContain("user").doesNotContain("password");
    }

    @Test
    void missingHostAndPortFallBackToLocalDefaults() {
        MockEnvironment env = new MockEnvironment();
        String resolved = env.resolveRequiredPlaceholders(PLACEHOLDER_URL);

        assertThat(resolved).isEqualTo("jdbc:postgresql://localhost:5432/payagent");
    }

    @Test
    void credentialsAreNotPartOfTheResolvedJdbcUrl() {
        MockEnvironment env = new MockEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "PAYAGENT_DB_HOST", "db.example.com",
                "PAYAGENT_DB_PORT", "5432",
                "PAYAGENT_DB_NAME", "agent_db",
                "PAYAGENT_DB_USER", "app_user",
                "PAYAGENT_DB_PASSWORD", "super-secret-password")));

        String resolved = env.resolveRequiredPlaceholders(PLACEHOLDER_URL);

        assertThat(resolved)
                .isEqualTo("jdbc:postgresql://db.example.com:5432/agent_db")
                .doesNotContain("app_user")
                .doesNotContain("super-secret-password");
    }

    @Test
    void applicationYmlNoLongerReferencesPayAgentDbUrl() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(text)
                .contains("jdbc:postgresql://${PAYAGENT_DB_HOST:localhost}:${PAYAGENT_DB_PORT:5432}/${PAYAGENT_DB_NAME:payagent}")
                .doesNotContain("${PAYAGENT_DB_URL");
    }
}
