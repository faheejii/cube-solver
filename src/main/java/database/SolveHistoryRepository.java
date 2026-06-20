package database;

import solver.CfopStageResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SolveHistoryRepository {
    private final DatabaseManager databaseManager;

    public SolveHistoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public SolveHistoryEntry createAttempt(CreateSolveAttemptCommand command) throws SQLException {
        try (var connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId = ensureUser(connection, command.userExternalId());
                var existing = findAttemptByClientId(connection, userId, command.clientAttemptId());
                if (existing != null) {
                    connection.commit();
                    return existing;
                }

                var insertedId = insertAttempt(connection, userId, command);
                if (insertedId == null) {
                    var concurrentAttempt = findAttemptByClientId(connection, userId, command.clientAttemptId());
                    if (concurrentAttempt == null) {
                        throw new SQLException("Idempotent attempt save could not be reloaded");
                    }
                    connection.commit();
                    return concurrentAttempt;
                }

                long solveId = insertedId;
                updateUserStats(connection, userId, command);
                var saved = findHistoryEntry(connection, solveId, userId);
                connection.commit();
                return saved;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public SavedSolution upsertSolution(SaveSolutionCommand command) throws SQLException {
        try (var connection = databaseManager.openConnection()) {
            long userId = requireUserId(connection, command.userExternalId());
            requireOwnedSolve(connection, command.solveId(), userId);
            upsertSolutionRow(connection, command);
            return findSolution(connection, command.solveId(), command.mode());
        }
    }

    public List<SolveHistoryEntry> listRecent(String userExternalId, int limit) throws SQLException {
        try (var connection = databaseManager.openConnection()) {
            var userId = findUserId(connection, userExternalId);
            if (userId == null) {
                return List.of();
            }
            try (var statement = connection.prepareStatement("""
                    SELECT s.id, s.client_attempt_id, s.scramble, s.cross_face_requested,
                           s.timer_ms, s.official_ms, s.penalty, s.dnf, s.created_at,
                           MAX(CASE WHEN ss.mode = 'greedy' THEN ss.cross_face_requested END) AS fast_cross,
                           MAX(CASE WHEN ss.mode = 'optimized' THEN ss.cross_face_requested END) AS optimized_cross
                    FROM solves s
                    LEFT JOIN solve_solutions ss ON ss.solve_id = s.id AND ss.status = 'ready'
                    WHERE s.user_id = ?
                    GROUP BY s.id
                    ORDER BY s.created_at DESC
                    LIMIT ?
                    """)) {
                statement.setLong(1, userId);
                statement.setInt(2, Math.max(1, Math.min(limit, 100)));
                return readHistoryEntries(statement);
            }
        }
    }

    public SolveHistoryDetail findDetail(String userExternalId, long solveId) throws SQLException {
        try (var connection = databaseManager.openConnection()) {
            long userId = requireUserId(connection, userExternalId);
            try (var statement = connection.prepareStatement("""
                    SELECT id, client_attempt_id, scramble, cross_face_requested,
                           timer_ms, official_ms, penalty, dnf, created_at
                    FROM solves
                    WHERE id = ? AND user_id = ?
                    """)) {
                statement.setLong(1, solveId);
                statement.setLong(2, userId);
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Solve not found");
                    }
                    return new SolveHistoryDetail(
                            result.getLong("id"),
                            result.getString("client_attempt_id"),
                            result.getString("scramble"),
                            result.getString("cross_face_requested"),
                            nullableInt(result, "timer_ms"),
                            nullableInt(result, "official_ms"),
                            result.getString("penalty"),
                            result.getBoolean("dnf"),
                            result.getObject("created_at", java.time.OffsetDateTime.class),
                            listSolutions(connection, solveId)
                    );
                }
            }
        }
    }

    private static List<SavedSolution> listSolutions(Connection connection, long solveId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT *
                FROM solve_solutions
                WHERE solve_id = ? AND status = 'ready'
                ORDER BY CASE WHEN mode = 'greedy' THEN 0 ELSE 1 END
                """)) {
            statement.setLong(1, solveId);
            try (var result = statement.executeQuery()) {
                var solutions = new ArrayList<SavedSolution>();
                while (result.next()) {
                    solutions.add(readSolution(result));
                }
                return solutions;
            }
        }
    }

    private static SavedSolution findSolution(Connection connection, long solveId, String mode) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT *
                FROM solve_solutions
                WHERE solve_id = ? AND mode = ?
                """)) {
            statement.setLong(1, solveId);
            statement.setString(2, mode);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Saved solution could not be reloaded");
                }
                return readSolution(result);
            }
        }
    }

    private static SavedSolution readSolution(java.sql.ResultSet result) throws SQLException {
        return new SavedSolution(
                result.getString("mode"),
                result.getString("cross_face_requested"),
                result.getString("cross_face_chosen"),
                result.getInt("f2l_setup_case_count"),
                result.getInt("f2l_insert_case_count"),
                readStage(result, "cross"),
                readStage(result, "f2l"),
                readStage(result, "oll"),
                readStage(result, "pll"),
                result.getString("solved_f2l_slots"),
                result.getBoolean("fully_solved"),
                result.getInt("total_moves"),
                result.getDouble("solve_elapsed_ms"),
                result.getString("solver_version"),
                result.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    private static CfopStageResult readStage(java.sql.ResultSet result, String stage) throws SQLException {
        return new CfopStageResult(
                stage,
                result.getString(stage + "_algorithm"),
                result.getInt(stage + "_moves"),
                result.getBoolean(stage + "_solved"),
                result.getString(stage + "_status")
        );
    }

    private static void upsertSolutionRow(Connection connection, SaveSolutionCommand command) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO solve_solutions (
                    solve_id, mode, status, cross_face_requested, cross_face_chosen,
                    solution, normalized_solution, f2l_setup_case_count, f2l_insert_case_count,
                    cross_algorithm, cross_moves, cross_solved, cross_status,
                    f2l_algorithm, f2l_moves, f2l_solved, f2l_status,
                    oll_algorithm, oll_moves, oll_solved, oll_status,
                    pll_algorithm, pll_moves, pll_solved, pll_status,
                    solved_f2l_slots, total_moves, solve_elapsed_ms, fully_solved, solver_version,
                    updated_at
                ) VALUES (
                    ?, ?, 'ready', ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, NOW()
                )
                ON CONFLICT (solve_id, mode) DO UPDATE SET
                    status = 'ready',
                    cross_face_requested = EXCLUDED.cross_face_requested,
                    cross_face_chosen = EXCLUDED.cross_face_chosen,
                    solution = EXCLUDED.solution,
                    normalized_solution = EXCLUDED.normalized_solution,
                    f2l_setup_case_count = EXCLUDED.f2l_setup_case_count,
                    f2l_insert_case_count = EXCLUDED.f2l_insert_case_count,
                    cross_algorithm = EXCLUDED.cross_algorithm,
                    cross_moves = EXCLUDED.cross_moves,
                    cross_solved = EXCLUDED.cross_solved,
                    cross_status = EXCLUDED.cross_status,
                    f2l_algorithm = EXCLUDED.f2l_algorithm,
                    f2l_moves = EXCLUDED.f2l_moves,
                    f2l_solved = EXCLUDED.f2l_solved,
                    f2l_status = EXCLUDED.f2l_status,
                    oll_algorithm = EXCLUDED.oll_algorithm,
                    oll_moves = EXCLUDED.oll_moves,
                    oll_solved = EXCLUDED.oll_solved,
                    oll_status = EXCLUDED.oll_status,
                    pll_algorithm = EXCLUDED.pll_algorithm,
                    pll_moves = EXCLUDED.pll_moves,
                    pll_solved = EXCLUDED.pll_solved,
                    pll_status = EXCLUDED.pll_status,
                    solved_f2l_slots = EXCLUDED.solved_f2l_slots,
                    total_moves = EXCLUDED.total_moves,
                    solve_elapsed_ms = EXCLUDED.solve_elapsed_ms,
                    fully_solved = EXCLUDED.fully_solved,
                    solver_version = EXCLUDED.solver_version,
                    updated_at = NOW()
                """)) {
            int i = 1;
            statement.setLong(i++, command.solveId());
            statement.setString(i++, command.mode());
            statement.setString(i++, command.crossFaceRequested());
            statement.setString(i++, command.crossFaceChosen());
            statement.setString(i++, command.solution());
            statement.setString(i++, command.normalizedSolution());
            statement.setInt(i++, command.f2lSetupCaseCount());
            statement.setInt(i++, command.f2lInsertCaseCount());
            statement.setString(i++, command.crossAlgorithm());
            statement.setInt(i++, command.crossMoves());
            statement.setBoolean(i++, command.crossSolved());
            statement.setString(i++, command.crossStatus());
            statement.setString(i++, command.f2lAlgorithm());
            statement.setInt(i++, command.f2lMoves());
            statement.setBoolean(i++, command.f2lSolved());
            statement.setString(i++, command.f2lStatus());
            statement.setString(i++, command.ollAlgorithm());
            statement.setInt(i++, command.ollMoves());
            statement.setBoolean(i++, command.ollSolved());
            statement.setString(i++, command.ollStatus());
            statement.setString(i++, command.pllAlgorithm());
            statement.setInt(i++, command.pllMoves());
            statement.setBoolean(i++, command.pllSolved());
            statement.setString(i++, command.pllStatus());
            statement.setString(i++, command.solvedF2LSlots());
            statement.setInt(i++, command.totalMoves());
            statement.setDouble(i++, command.solveElapsedMs());
            statement.setBoolean(i++, command.fullySolved());
            statement.setString(i, command.solverVersion());
            statement.executeUpdate();
        }
    }

    private static Long findUserId(Connection connection, String externalId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT id FROM users WHERE external_id = ?")) {
            statement.setString(1, externalId);
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong("id") : null;
            }
        }
    }

    private static long requireUserId(Connection connection, String externalId) throws SQLException {
        var userId = findUserId(connection, externalId);
        if (userId == null) {
            throw new IllegalArgumentException("User not found");
        }
        return userId;
    }

    private static long ensureUser(Connection connection, String externalId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO users (external_id)
                VALUES (?)
                ON CONFLICT (external_id) DO UPDATE SET updated_at = NOW()
                RETURNING id
                """)) {
            statement.setString(1, externalId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong("id");
            }
        }
    }

    private static SolveHistoryEntry findAttemptByClientId(
            Connection connection,
            long userId,
            String clientAttemptId
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT s.id, s.client_attempt_id, s.scramble, s.cross_face_requested,
                       s.timer_ms, s.official_ms, s.penalty, s.dnf, s.created_at,
                       MAX(CASE WHEN ss.mode = 'greedy' THEN ss.cross_face_requested END) AS fast_cross,
                       MAX(CASE WHEN ss.mode = 'optimized' THEN ss.cross_face_requested END) AS optimized_cross
                FROM solves s
                LEFT JOIN solve_solutions ss ON ss.solve_id = s.id AND ss.status = 'ready'
                WHERE s.user_id = ? AND s.client_attempt_id = ?
                GROUP BY s.id
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, clientAttemptId);
            var entries = readHistoryEntries(statement);
            return entries.isEmpty() ? null : entries.get(0);
        }
    }

    private static Long insertAttempt(
            Connection connection,
            long userId,
            CreateSolveAttemptCommand command
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO solves (
                    user_id, client_attempt_id, scramble, cross_face_requested,
                    timer_ms, penalty, official_ms, dnf
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, client_attempt_id) DO NOTHING
                RETURNING id
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, command.clientAttemptId());
            statement.setString(3, command.scramble());
            statement.setString(4, command.crossFaceRequested());
            setNullableInt(statement, 5, command.timerMs());
            statement.setString(6, command.penalty());
            setNullableInt(statement, 7, command.officialMs());
            statement.setBoolean(8, command.dnf());
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong("id") : null;
            }
        }
    }

    private static void updateUserStats(
            Connection connection,
            long userId,
            CreateSolveAttemptCommand command
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO user_stats (
                    user_id, solve_count, dnf_count, best_single_ms,
                    latest_official_ms, latest_solve_at, updated_at
                ) VALUES (?, 1, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    solve_count = user_stats.solve_count + 1,
                    dnf_count = user_stats.dnf_count + EXCLUDED.dnf_count,
                    best_single_ms = CASE
                        WHEN EXCLUDED.best_single_ms IS NULL THEN user_stats.best_single_ms
                        WHEN user_stats.best_single_ms IS NULL THEN EXCLUDED.best_single_ms
                        ELSE LEAST(user_stats.best_single_ms, EXCLUDED.best_single_ms)
                    END,
                    latest_official_ms = EXCLUDED.latest_official_ms,
                    latest_solve_at = NOW(),
                    updated_at = NOW()
                """)) {
            statement.setLong(1, userId);
            statement.setInt(2, command.dnf() ? 1 : 0);
            setNullableInt(statement, 3, command.dnf() ? null : command.officialMs());
            setNullableInt(statement, 4, command.officialMs());
            statement.executeUpdate();
        }
    }

    private static SolveHistoryEntry findHistoryEntry(Connection connection, long solveId, long userId)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT s.id, s.client_attempt_id, s.scramble, s.cross_face_requested,
                       s.timer_ms, s.official_ms, s.penalty, s.dnf, s.created_at,
                       MAX(CASE WHEN ss.mode = 'greedy' THEN ss.cross_face_requested END) AS fast_cross,
                       MAX(CASE WHEN ss.mode = 'optimized' THEN ss.cross_face_requested END) AS optimized_cross
                FROM solves s
                LEFT JOIN solve_solutions ss ON ss.solve_id = s.id AND ss.status = 'ready'
                WHERE s.id = ? AND s.user_id = ?
                GROUP BY s.id
                """)) {
            statement.setLong(1, solveId);
            statement.setLong(2, userId);
            var entries = readHistoryEntries(statement);
            if (entries.isEmpty()) {
                throw new SQLException("Saved attempt could not be reloaded");
            }
            return entries.get(0);
        }
    }

    private static void requireOwnedSolve(Connection connection, long solveId, long userId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM solves WHERE id = ? AND user_id = ?"
        )) {
            statement.setLong(1, solveId);
            statement.setLong(2, userId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Solve not found");
                }
            }
        }
    }

    private static List<SolveHistoryEntry> readHistoryEntries(PreparedStatement statement) throws SQLException {
        try (var result = statement.executeQuery()) {
            var entries = new ArrayList<SolveHistoryEntry>();
            while (result.next()) {
                entries.add(new SolveHistoryEntry(
                        result.getLong("id"),
                        result.getString("client_attempt_id"),
                        result.getString("scramble"),
                        result.getString("cross_face_requested"),
                        nullableInt(result, "timer_ms"),
                        nullableInt(result, "official_ms"),
                        result.getString("penalty"),
                        result.getBoolean("dnf"),
                        result.getString("fast_cross"),
                        result.getString("optimized_cross"),
                        result.getObject("created_at", java.time.OffsetDateTime.class)
                ));
            }
            return entries;
        }
    }

    private static Integer nullableInt(java.sql.ResultSet result, String column) throws SQLException {
        var value = result.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }
}
