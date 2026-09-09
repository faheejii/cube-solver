package database.persistence.repository;

import database.CreateSolveAttemptCommand;

public interface SolveWriteRepository {
    /** Inserts once using the existing unique idempotency key and returns null on conflict. */
    Long insertIfAbsent(long userId, CreateSolveAttemptCommand command);

    /** Deletes only an attempt owned by the given user. */
    int deleteOwnedById(long solveId, long userId);
}
