package test;

import database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void initialize_shouldApplyFlywayHistoryAndAuthenticationSchema() throws Exception {
        try (var database = PostgresTestDatabase.create()) {
            database.manager().initialize();

            try (var connection = database.manager().openConnection(); var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success")) {
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
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void initialize_shouldUpgradeLegacySchemaWithoutLosingSolveData() throws Exception {
        try (var database = PostgresTestDatabase.create()) {
            createLegacySchema(database.manager());
            database.manager().initialize();

            assertLegacyRowMigrated(database.manager());
            database.manager().initialize();
            assertLegacyRowMigrated(database.manager());
        }
    }

    private static void createLegacySchema(DatabaseManager database) throws SQLException {
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE users (
                        id BIGSERIAL PRIMARY KEY,
                        external_id TEXT NOT NULL UNIQUE,
                        display_name TEXT,
                        email TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            statement.execute("""
                    CREATE TABLE solves (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                        session_id BIGINT,
                        scramble TEXT NOT NULL,
                        cross_face_requested TEXT NOT NULL,
                        timer_ms INTEGER,
                        penalty TEXT NOT NULL DEFAULT 'none',
                        official_ms INTEGER,
                        dnf BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        cross_face_chosen TEXT NOT NULL,
                        f2l_mode TEXT NOT NULL,
                        solution TEXT NOT NULL,
                        normalized_solution TEXT,
                        cross_algorithm TEXT NOT NULL,
                        cross_moves INTEGER NOT NULL,
                        cross_solved BOOLEAN NOT NULL,
                        cross_status TEXT NOT NULL,
                        f2l_algorithm TEXT NOT NULL,
                        f2l_moves INTEGER NOT NULL,
                        f2l_solved BOOLEAN NOT NULL,
                        f2l_status TEXT NOT NULL,
                        oll_algorithm TEXT NOT NULL,
                        oll_moves INTEGER NOT NULL,
                        oll_solved BOOLEAN NOT NULL,
                        oll_status TEXT NOT NULL,
                        pll_algorithm TEXT NOT NULL,
                        pll_moves INTEGER NOT NULL,
                        pll_solved BOOLEAN NOT NULL,
                        pll_status TEXT NOT NULL,
                        solved_f2l_slots TEXT NOT NULL,
                        total_moves INTEGER NOT NULL,
                        solve_elapsed_ms DOUBLE PRECISION NOT NULL,
                        fully_solved BOOLEAN NOT NULL,
                        solver_version TEXT
                    )
                    """);
            statement.execute("""
                    INSERT INTO users (external_id, display_name, email)
                    VALUES ('legacy-user', 'Legacy User', 'legacy@example.com')
                    """);
            statement.execute("""
                    INSERT INTO solves (
                        user_id, scramble, cross_face_requested, timer_ms, penalty, official_ms, dnf,
                        cross_face_chosen, f2l_mode, solution, normalized_solution,
                        cross_algorithm, cross_moves, cross_solved, cross_status,
                        f2l_algorithm, f2l_moves, f2l_solved, f2l_status,
                        oll_algorithm, oll_moves, oll_solved, oll_status,
                        pll_algorithm, pll_moves, pll_solved, pll_status,
                        solved_f2l_slots, total_moves, solve_elapsed_ms, fully_solved, solver_version
                    )
                    SELECT id, 'R U', 'F', 1234, 'none', 1234, FALSE,
                           'F', 'greedy', 'R U', 'R U',
                           'R', 1, TRUE, 'solved',
                           'U', 1, TRUE, 'solved',
                           'R U', 2, TRUE, 'solved',
                           'R U', 2, TRUE, 'solved',
                           'all', 6, 2.5, TRUE, 'legacy-v0'
                    FROM users WHERE external_id = 'legacy-user'
                    """);
        }
    }

    private static void assertLegacyRowMigrated(DatabaseManager database) throws SQLException {
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT s.id, s.client_attempt_id, s.scramble, s.cross_face_requested,
                           ss.mode, ss.cross_face_chosen, ss.solution, ss.cross_algorithm,
                           ss.f2l_algorithm, ss.oll_algorithm, ss.pll_algorithm, ss.fully_solved
                    FROM solves s
                    JOIN solve_solutions ss ON ss.solve_id = s.id
                    WHERE s.user_id = (SELECT id FROM users WHERE external_id = 'legacy-user')
                    """)) {
                assertTrue(result.next());
                var solveId = result.getLong("id");
                assertEquals("legacy-" + solveId, result.getString("client_attempt_id"));
                assertEquals("R U", result.getString("scramble"));
                assertEquals("F", result.getString("cross_face_requested"));
                assertEquals("greedy", result.getString("mode"));
                assertEquals("F", result.getString("cross_face_chosen"));
                assertEquals("R U", result.getString("solution"));
                assertEquals("R", result.getString("cross_algorithm"));
                assertEquals("U", result.getString("f2l_algorithm"));
                assertEquals("R U", result.getString("oll_algorithm"));
                assertEquals("R U", result.getString("pll_algorithm"));
                assertTrue(result.getBoolean("fully_solved"));
                assertFalse(result.next());
            }

            assertEquals(1, count(statement, "SELECT COUNT(*) FROM solve_solutions"));
            assertEquals(1, count(statement, "SELECT COUNT(*) FROM solves WHERE client_attempt_id = 'legacy-1'"));
            assertEquals(0, count(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = current_schema() AND table_name = 'solves'
                      AND column_name IN ('f2l_mode', 'solution', 'cross_algorithm', 'pll_algorithm')
                    """));
            assertEquals(1, count(statement, """
                    SELECT COUNT(*) FROM pg_constraint
                    WHERE connamespace = current_schema()::regnamespace
                      AND conname = 'uq_solve_solutions_solve_mode'
                    """));
            assertEquals(1, count(statement, """
                    SELECT COUNT(*) FROM pg_indexes
                    WHERE schemaname = current_schema() AND indexname = 'uq_solves_user_client_attempt'
                    """));
            assertNotNull(statement.executeQuery("SELECT id FROM auth_sessions LIMIT 1"));
        }
    }

    private static int count(java.sql.Statement statement, String sql) throws SQLException {
        try (var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
