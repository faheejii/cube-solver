package server;

import database.SaveSolutionCommand;

/** Persists a completed solve without coupling job orchestration to a storage implementation. */
interface CompletedSolutionPersistence {
    boolean isConfigured();

    void save(SaveSolutionCommand command) throws Exception;
}
