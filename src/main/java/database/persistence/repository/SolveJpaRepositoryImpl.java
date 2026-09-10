package database.persistence.repository;

import database.CreateSolveAttemptCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public final class SolveJpaRepositoryImpl implements SolveWriteRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Long insertIfAbsent(long userId, CreateSolveAttemptCommand command) {
        var query = entityManager.createNativeQuery("""
                INSERT INTO solves (
                    user_id, client_attempt_id, scramble, cross_face_requested,
                    timer_ms, penalty, official_ms, dnf
                ) VALUES (
                    :userId, :clientAttemptId, :scramble, :crossFaceRequested,
                    :timerMs, :penalty, :officialMs, :dnf
                )
                ON CONFLICT (user_id, client_attempt_id) DO NOTHING
                RETURNING id
                """);
        query.setParameter("userId", userId);
        query.setParameter("clientAttemptId", command.clientAttemptId());
        query.setParameter("scramble", command.scramble());
        query.setParameter("crossFaceRequested", command.crossFaceRequested());
        query.setParameter("timerMs", command.timerMs());
        query.setParameter("penalty", command.penalty());
        query.setParameter("officialMs", command.officialMs());
        query.setParameter("dnf", command.dnf());
        var rows = query.getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).longValue();
    }

    @Override
    public int deleteOwnedById(long solveId, long userId) {
        return entityManager.createNativeQuery("""
                DELETE FROM solves WHERE id = :solveId AND user_id = :userId
                """)
                .setParameter("solveId", solveId)
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
