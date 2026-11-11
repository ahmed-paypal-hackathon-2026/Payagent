package com.payagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DbUrlEnvironmentPostProcessorTest {

    private final DbUrlEnvironmentPostProcessor processor = new DbUrlEnvironmentPostProcessor();

    @Test
    void jdbcUrlIsLeftUnchanged() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PAYAGENT_DB_URL", "jdbc:postgresql://localhost:5432/payagent");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("PAYAGENT_DB_URL")).isEqualTo("jdbc:postgresql://localhost:5432/payagent");
    }

    @Test
    void postgresqlSchemeIsConvertedToJdbc() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PAYAGENT_DB_URL", "postgresql://user:pass@host:5432/payagent");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("PAYAGENT_DB_URL")).isEqualTo("jdbc:postgresql://user:pass@host:5432/payagent");
    }

    @Test
    void postgresSchemeIsConvertedToJdbc() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PAYAGENT_DB_URL", "postgres://user:pass@host:5432/payagent");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("PAYAGENT_DB_URL")).isEqualTo("jdbc:postgresql://user:pass@host:5432/payagent");
    }

    @Test
    void unsupportedSchemeIsLeftUnchanged() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("PAYAGENT_DB_URL", "mysql://user:pass@host:5432/payagent");

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("PAYAGENT_DB_URL")).isEqualTo("mysql://user:pass@host:5432/payagent");
    }
}
