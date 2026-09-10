package server;

import database.SaveSolutionCommand;
import database.persistence.entity.SpringHistoryPersistenceService;

import javax.sql.DataSource;

/** Spring adapter for the solve-job completion persistence port. */
final class SpringCompletedSolutionPersistence implements CompletedSolutionPersistence {
    private final SpringHistoryPersistenceService history;
    private final boolean configured;

    SpringCompletedSolutionPersistence(SpringHistoryPersistenceService history, DataSource dataSource) {
        this.history = history;
        this.configured = dataSource != null;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void save(SaveSolutionCommand command) {
        history.upsertSolution(command);
    }
}
