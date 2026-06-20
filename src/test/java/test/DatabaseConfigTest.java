package test;

import database.DatabaseConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseConfigTest {
    @Test
    void fromConnectionString_shouldConvertStandardPostgresUrlToJdbc() {
        var config = DatabaseConfig.fromConnectionString(
                "postgresql://user:secret@example.neon.tech/neondb?sslmode=require&channel_binding=require"
        );

        assertTrue(config.configured());
        assertEquals(
                "jdbc:postgresql://example.neon.tech/neondb?sslmode=require&channel_binding=require",
                config.jdbcUrl()
        );
        assertEquals("user", config.username());
        assertEquals("secret", config.password());
    }

    @Test
    void fromConnectionString_shouldPreserveJdbcUrl() {
        var config = DatabaseConfig.fromConnectionString(
                "jdbc:postgresql://example.neon.tech/neondb?sslmode=require"
        );

        assertTrue(config.configured());
        assertEquals("jdbc:postgresql://example.neon.tech/neondb?sslmode=require", config.jdbcUrl());
    }

    @Test
    void fromConnectionString_shouldPreservePlusInCredentials() {
        var config = DatabaseConfig.fromConnectionString(
                "postgresql://user:secret+suffix@example.neon.tech/neondb?sslmode=require"
        );

        assertEquals("secret+suffix", config.password());
    }

    @Test
    void disabled_shouldRepresentMissingDatabase() {
        var config = DatabaseConfig.disabled();

        assertFalse(config.configured());
    }
}
