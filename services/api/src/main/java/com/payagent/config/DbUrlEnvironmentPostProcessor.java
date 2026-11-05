package com.payagent.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

public class DbUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_NAME = "PAYAGENT_DB_URL";
    private static final String JDBC_PREFIX = "jdbc:";
    private static final String POSTGRES_URI_PREFIX = "postgresql://";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty(PROPERTY_NAME);
        if (url == null || url.isBlank()) {
            return;
        }
        if (url.startsWith(JDBC_PREFIX)) {
            return;
        }
        if (url.startsWith(POSTGRES_URI_PREFIX)) {
            String jdbcUrl = JDBC_PREFIX + url;
            Map<String, Object> map = Map.of(PROPERTY_NAME, jdbcUrl);
            MapPropertySource source = new MapPropertySource("dbUrlConverter", map);
            environment.getPropertySources().addFirst(source);
        }
    }
}
