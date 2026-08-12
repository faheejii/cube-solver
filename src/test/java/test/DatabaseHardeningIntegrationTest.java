package test;

import database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseHardeningIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void configuredAdminEmail_shouldPromoteExistingAccountIdempotently() throws Exception {
        var previousAdminEmail = System.getProperty("admin.email");
        var adminEmail = "bootstrap-" + UUID.randomUUID() + "@example.com";
        System.setProperty("admin.email", adminEmail);

        try (var postgres = PostgresTestDatabase.create()) {
            var database = postgres.manager();
            database.initialize();
            insertUser(database, "bootstrap-user", adminEmail);
            insertUser(database, "ordinary-user", "ordinary-" + UUID.randomUUID() + "@example.com");

            database.initialize();
            assertEquals("admin", role(database, adminEmail));
            assertEquals("user", role(database, "ordinary-user"));

            database.initialize();
            assertEquals("admin", role(database, adminEmail));
            assertEquals("user", role(database, "ordinary-user"));
        } finally {
            restoreProperty("admin.email", previousAdminEmail);
        }
    }

    private static void insertUser(DatabaseManager database, String externalId, String email) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO users (external_id, email, display_name)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setString(1, externalId);
            statement.setString(2, email);
            statement.setString(3, externalId);
            statement.executeUpdate();
        }
    }

    private static String role(DatabaseManager database, String emailOrExternalId) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     SELECT role
                     FROM users
                     WHERE email = ? OR external_id = ?
                     """)) {
            statement.setString(1, emailOrExternalId);
            statement.setString(2, emailOrExternalId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new AssertionError("Missing user " + emailOrExternalId);
                }
                return result.getString("role");
            }
        }
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }
}
