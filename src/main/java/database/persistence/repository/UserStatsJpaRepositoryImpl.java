package database.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public final class UserStatsJpaRepositoryImpl implements UserStatsWriteRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void updateAfterSolve(long userId, Integer officialMs, boolean dnf) {
        entityManager.createNativeQuery("""
                INSERT INTO user_stats (
                    user_id, solve_count, dnf_count, best_single_ms,
                    latest_official_ms, latest_solve_at, updated_at
                ) VALUES (
                    :userId, 1, :dnfCount, :bestSingleMs,
                    :officialMs, NOW(), NOW()
                )
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
                """)
                .setParameter("userId", userId)
                .setParameter("dnfCount", dnf ? 1 : 0)
                .setParameter("bestSingleMs", dnf ? null : officialMs)
                .setParameter("officialMs", officialMs)
                .executeUpdate();
    }

    @Override
    public void rebuild(long userId) {
        entityManager.createNativeQuery("""
                INSERT INTO user_stats (
                    user_id, solve_count, dnf_count, best_single_ms,
                    latest_official_ms, latest_solve_at, updated_at
                )
                SELECT
                    :userId,
                    COUNT(*)::INTEGER,
                    COUNT(*) FILTER (WHERE dnf)::INTEGER,
                    MIN(official_ms) FILTER (WHERE NOT dnf),
                    (
                        SELECT recent.official_ms FROM solves recent
                        WHERE recent.user_id = :userId
                        ORDER BY recent.created_at DESC, recent.id DESC LIMIT 1
                    ),
                    (
                        SELECT recent.created_at FROM solves recent
                        WHERE recent.user_id = :userId
                        ORDER BY recent.created_at DESC, recent.id DESC LIMIT 1
                    ),
                    NOW()
                FROM solves
                WHERE user_id = :userId
                ON CONFLICT (user_id) DO UPDATE SET
                    solve_count = EXCLUDED.solve_count,
                    dnf_count = EXCLUDED.dnf_count,
                    best_single_ms = EXCLUDED.best_single_ms,
                    latest_official_ms = EXCLUDED.latest_official_ms,
                    latest_solve_at = EXCLUDED.latest_solve_at,
                    updated_at = NOW()
                """)
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
