package test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void initialize_shouldApplyFlywayHistoryAndAuthenticationSchema() throws Exception {
        try (var database = PostgresTestDatabase.create()) {
            database.initialize();

            try (var connection = database.openConnection(); var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success")) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'solve_solutions'
                          AND column_name IN ('f2l_trace_json', 'comparison_json')
                        """)) {
                    assertTrue(result.next());
                    assertEquals(2, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name IN ('users', 'solves', 'solve_solutions', 'auth_sessions')
                        """)) {
                    assertTrue(result.next());
                    assertEquals(4, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'users'
                          AND column_name = 'role'
                        """)) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM pg_constraint
                        WHERE connamespace = current_schema()::regnamespace
                          AND conname = 'ck_users_role'
                        """)) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'solve_solutions'
                          AND column_name = 'f2l_trace_json'
                        """)) {
                    assertTrue(result.next());
                    assertEquals("NO", result.getString(1));
                }
            }
        }
    }
}
