package test.database;

import test.support.PostgresTestDatabase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void initialize_shouldApplyFlywayHistoryAndCurrentSchema() throws Exception {
        try (var database = PostgresTestDatabase.create()) {
            database.initialize();

            try (var connection = database.openConnection(); var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success")) {
                    assertTrue(result.next());
                    assertEquals(8, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM pg_trigger trigger_row
                        JOIN pg_class table_row ON table_row.oid = trigger_row.tgrelid
                        WHERE table_row.relnamespace = current_schema()::regnamespace
                          AND trigger_row.tgname IN (
                              'trg_algorithm_catalog_algorithm_delete_guard',
                              'trg_algorithm_catalog_revision_delete_guard',
                              'trg_algorithm_catalog_event_delete_guard'
                          )
                          AND NOT trigger_row.tgisinternal
                        """)) {
                    assertTrue(result.next());
                    assertEquals(3, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name IN (
                              'algorithm_catalog_state', 'algorithm_catalog_algorithms',
                              'algorithm_catalog_revisions', 'algorithm_catalog_workflow_events',
                              'user_disabled_algorithm_ids', 'user_enabled_trial_revision_ids',
                              'algorithm_catalog_legacy_aliases'
                          )
                        """)) {
                    assertTrue(result.next());
                    assertEquals(7, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'solve_solutions'
                          AND column_name IN ('f2l_trace_json', 'comparison_json', 'disabled_algorithms_json',
                                              'algorithm_manifest_json')
                        """)) {
                    assertTrue(result.next());
                    assertEquals(4, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'algorithm_catalog_state'
                          AND column_name = 'catalog_version'
                        """)) {
                    assertTrue(result.next());
                    assertEquals(0, result.getInt(1));
                }
                try (var result = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name IN ('users', 'solves', 'solve_solutions', 'auth_sessions', 'user_disabled_algorithms')
                        """)) {
                    assertTrue(result.next());
                    assertEquals(5, result.getInt(1));
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
