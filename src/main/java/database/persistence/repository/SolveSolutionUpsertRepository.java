package database.persistence.repository;

import database.persistence.entity.SolveSolutionEntity;

public interface SolveSolutionUpsertRepository {
    /** Uses the existing (solve_id, mode) unique key to make solution writes idempotent. */
    void upsert(SolveSolutionEntity solution);
}
