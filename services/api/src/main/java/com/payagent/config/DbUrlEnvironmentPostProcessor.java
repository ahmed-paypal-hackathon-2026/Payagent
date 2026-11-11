package com.payagent.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;
import java.util.List;

public class DbUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_NAME = "PAYAGENT_DB_URL";
    private static final String JDBC_PREFIX = "jdbc:";
    private static final List<String> POSTGRES_URI_PREFIXES = List.of("postgresql://", "postgres://");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty(PROPERTY_NAME);
        if (url == null || url.isBlank()) {
            return;
        }
        if (url.startsWith(JDBC_PREFIX)) {
            return;
        }
        for (String prefix : POSTGRES_URI_PREFIXES) {
            if (url.startsWith(prefix)) {
                String jdbcUrl = "jdbc:postgresql://" + url.substring(prefix.length());
                Map<String, Object> map = Map.of(PROPERTY_NAME, jdbcUrl);
                MapPropertySource source = new MapPropertySource("dbUrlConverter", map);
                environment.getPropertySources().addFirst(source);
                return;
            }
        }
    }
}
