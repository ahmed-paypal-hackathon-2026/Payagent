package com.payagent.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Flyway migration history against a disposable PostgreSQL
 * database. These tests are skipped unless the dedicated environment
 * variables are provided; they never point at the production database.
 */
class FlywayPostgreSqlVerificationTest {

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String user() {
        return System.getenv("PAYAGENT_B17_PG_USER");
    }

    private static String password() {
        return System.getenv("PAYAGENT_B17_PG_PASSWORD");
    }

    @Test
    void freshDatabaseAppliesAllMigrations() throws Exception {
        String url = env("PAYAGENT_B17_FRESH_URL");
        assumeTrue(url != null && !url.isBlank(), "Fresh PG test database not provided");

        Flyway.configure().dataSource(url, user(), password()).load().migrate();

        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
            ResultSet rs = ps.executeQuery();
            List<String> versions = new ArrayList<>();
            while (rs.next()) {
                assertTrue(rs.getBoolean("success"), "Migration " + rs.getString("version") + " did not succeed");
                versions.add(rs.getString("version"));
            }
            assertTrue(versions.contains("1"), "V1 migration missing from history: " + versions);
            assertTrue(versions.contains("2"), "V2 migration missing from history: " + versions);
            assertTrue(versions.contains("3"), "V3 migration missing from history: " + versions);
            assertTrue(versions.contains("4"), "V4 migration missing from history: " + versions);
            assertTrue(versions.contains("5"), "V5 migration missing from history: " + versions);
            assertTrue(versions.contains("6"), "V6 migration missing from history: " + versions);
        }

        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "SELECT data_type, character_maximum_length FROM information_schema.columns WHERE table_name='agreements' AND column_name='terms_hash_sha256'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("character varying", rs.getString("data_type"));
            assertEquals(64, rs.getInt("character_maximum_length"));
        }
    }

    @Test
    void v1DatabaseUpgradesToLatestPreservingHashValues() throws Exception {
        String url = env("PAYAGENT_B17_UPGRADE_URL");
        assumeTrue(url != null && !url.isBlank(), "Upgrade PG test database not provided");

        Flyway.configure()
                .dataSource(url, user(), password())
                .target("1")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "SELECT data_type, character_maximum_length FROM information_schema.columns WHERE table_name='agreements' AND column_name='terms_hash_sha256'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("character varying", rs.getString("data_type"));
            assertEquals(64, rs.getInt("character_maximum_length"));
        }

        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (id, email, password_hash) VALUES ('11111111-1111-1111-1111-111111111111', 'upgrade-test@example.com', 'hash')")) {
            ps.executeUpdate();
        }
        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO deals (id, buyer_user_id, status) VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'AGREED')")) {
            ps.executeUpdate();
        }
        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO agreements (id, deal_id, version, canonical_terms_json, terms_hash_sha256) VALUES ('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 1, '{}', ?)")) {
            ps.setString(1, hash);
            ps.executeUpdate();
        }

        Flyway.configure()
                .dataSource(url, user(), password())
                .target("latest")
                .load()
                .migrate();

        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "SELECT data_type, character_maximum_length FROM information_schema.columns WHERE table_name='agreements' AND column_name='terms_hash_sha256'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("character varying", rs.getString("data_type"));
            assertEquals(64, rs.getInt("character_maximum_length"));
        }
        try (Connection c = DriverManager.getConnection(url, user(), password()); PreparedStatement ps = c.prepareStatement(
                "SELECT terms_hash_sha256 FROM agreements WHERE id='33333333-3333-3333-3333-333333333333'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(hash, rs.getString(1));
        }
    }
}
