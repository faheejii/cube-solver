package test;

import com.fasterxml.jackson.databind.ObjectMapper;
import database.CreateSolveAttemptCommand;
import database.DatabaseManager;
import database.SaveSolutionCommand;
import database.SolveHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolveHistoryHardeningIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void history_shouldEnforceOwnershipAndReloadTraceComparisonAndLegacyFallback() throws Exception {
        try (var postgres = PostgresTestDatabase.create()) {
            var database = postgres.manager();
            database.initialize();
            var owner = "owner-" + UUID.randomUUID();
            var other = "other-" + UUID.randomUUID();
            insertUser(database, owner);
            insertUser(database, other);

            var repository = new SolveHistoryRepository(database);
            var ownerAttempt = repository.createAttempt(attempt(owner, "owner-attempt"));
            var otherAttempt = repository.createAttempt(attempt(other, "other-attempt"));
            var legacyAttempt = repository.createAttempt(attempt(owner, "legacy-attempt"));

            var trace = "{\"traceComplete\":true,\"pairs\":[{\"order\":1,\"algorithm\":\"R U\"}]}";
            var comparison = "{\"totalMoveDifference\":-1,\"pairOrderChanged\":true}";
            var saved = repository.upsertSolution(solution(
                    owner,
                    ownerAttempt.id(),
                    "greedy",
                    trace,
                    comparison
            ));

            assertEquals(JSON.readTree(trace), JSON.readTree(saved.f2lTraceJson()));
            assertEquals(JSON.readTree(comparison), JSON.readTree(saved.comparisonJson()));

            var ownerDetail = repository.findDetail(owner, ownerAttempt.id());
            assertEquals(1, ownerDetail.solutions().size());
            assertEquals(JSON.readTree(trace), JSON.readTree(ownerDetail.solutions().get(0).f2lTraceJson()));
            assertEquals(JSON.readTree(comparison), JSON.readTree(ownerDetail.solutions().get(0).comparisonJson()));
            var ownerPage = repository.listPage(owner, 20, null);
            assertEquals(2, ownerPage.items().size());
            assertTrue(ownerPage.items().stream().anyMatch(entry -> entry.id() == ownerAttempt.id()));
            assertTrue(ownerPage.items().stream().anyMatch(entry -> entry.id() == legacyAttempt.id()));
            assertEquals(1, repository.listPage(other, 20, null).items().size());
            assertEquals(otherAttempt.id(), repository.listPage(other, 20, null).items().get(0).id());

            assertThrows(IllegalArgumentException.class,
                    () -> repository.findDetail(other, ownerAttempt.id()));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.deleteSolve(other, ownerAttempt.id()));
            assertThrows(IllegalArgumentException.class,
                    () -> repository.upsertSolution(solution(other, ownerAttempt.id(), "optimized", trace, comparison)));
            assertEquals(ownerAttempt.id(), repository.findDetail(owner, ownerAttempt.id()).id());

            var legacy = repository.upsertSolution(solution(owner, legacyAttempt.id(), "greedy", null, null));
            assertEquals("R U", legacy.f2l().algorithm());
            assertTrue(legacy.f2lTraceJson() == null);
            assertTrue(legacy.comparisonJson() == null);
            var legacyReloaded = repository.findDetail(owner, legacyAttempt.id());
            assertEquals(1, legacyReloaded.solutions().size());
            assertTrue(legacyReloaded.solutions().get(0).f2lTraceJson() == null);
            assertTrue(legacyReloaded.solutions().get(0).comparisonJson() == null);
        }
    }

    private static CreateSolveAttemptCommand attempt(String owner, String clientAttemptId) {
        return new CreateSolveAttemptCommand(owner, clientAttemptId, "R U F2", "D", 1234, "none", 1234, false);
    }

    private static SaveSolutionCommand solution(
            String owner,
            long solveId,
            String mode,
            String trace,
            String comparison
    ) {
        return new SaveSolutionCommand(
                owner, solveId, mode, "D", "D", "D R U R U'", "D R U R U'", 3, 4,
                "[FR]", 6, true, 12.5,
                "D", 1, true, "solved",
                "R U R U'", 4, true, "solved",
                "R U", 2, true, "solved",
                "R U'", 2, true, "solved",
                DatabaseManager.SOLVER_VERSION, trace, comparison
        );
    }

    private static void insertUser(DatabaseManager database, String externalId) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("""
                     INSERT INTO users (external_id, email, display_name)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setString(1, externalId);
            statement.setString(2, externalId + "@example.com");
            statement.setString(3, externalId);
            statement.executeUpdate();
        }
    }
}
