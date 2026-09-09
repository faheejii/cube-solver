package server;

import database.DatabaseManager;
import database.SaveSolutionCommand;
import database.SolveHistoryRepository;

/** Compatibility adapter used by the legacy HTTP server constructors. */
final class LegacySolveHistoryPersistence implements CompletedSolutionPersistence {
    private final DatabaseManager databaseManager;
    private final SolveHistoryRepository repository;

    LegacySolveHistoryPersistence(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.repository = new SolveHistoryRepository(databaseManager);
    }

    @Override
    public boolean isConfigured() {
        return databaseManager.isConfigured();
    }

    @Override
    public void save(SaveSolutionCommand command) throws Exception {
        repository.upsertSolution(command);
    }
}
