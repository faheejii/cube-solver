package database.persistence.repository;

import database.persistence.entity.SolveEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.List;

public interface SolveJpaRepository extends JpaRepository<SolveEntity, Long>, SolveWriteRepository {
    @Query("""
            select solve from SolveEntity solve
            where solve.user.id = :userId
              and solve.clientAttemptId = :clientAttemptId
            """)
    Optional<SolveEntity> findByUserIdAndClientAttemptId(
            @Param("userId") long userId,
            @Param("clientAttemptId") String clientAttemptId
    );

    @Query("""
            select solve from SolveEntity solve
            where solve.id = :solveId and solve.user.id = :userId
            """)
    Optional<SolveEntity> findOwnedById(@Param("solveId") long solveId, @Param("userId") long userId);

    /**
     * The tuple comparison is intentional: it preserves the existing keyset cursor
     * semantics and avoids offset pagination over a growing solve history.
     */
    @Query(value = """
            SELECT s.id AS "id",
                   s.client_attempt_id AS "clientAttemptId",
                   s.scramble AS "scramble",
                   s.cross_face_requested AS "crossFaceRequested",
                   s.timer_ms AS "timerMs",
                   s.official_ms AS "officialMs",
                   s.penalty AS "penalty",
                   s.dnf AS "dnf",
                   s.created_at AS "createdAt",
                   MAX(CASE WHEN ss.mode = 'greedy' THEN ss.cross_face_requested END) AS "fastCross",
                   MAX(CASE WHEN ss.mode = 'optimized' THEN ss.cross_face_requested END) AS "optimizedCross"
            FROM solves s
            LEFT JOIN solve_solutions ss
              ON ss.solve_id = s.id AND ss.status = 'ready'
            WHERE s.user_id = :userId
              AND (
                    CAST(:cursorCreatedAt AS TIMESTAMP WITH TIME ZONE) IS NULL
                    OR (s.created_at, s.id) < (
                        CAST(:cursorCreatedAt AS TIMESTAMP WITH TIME ZONE),
                        CAST(:cursorId AS BIGINT)
                    )
                  )
            GROUP BY s.id
            ORDER BY s.created_at DESC, s.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<HistoryRowProjection> findHistoryPage(
            @Param("userId") long userId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT s.id AS "id",
                   s.client_attempt_id AS "clientAttemptId",
                   s.scramble AS "scramble",
                   s.cross_face_requested AS "crossFaceRequested",
                   s.timer_ms AS "timerMs",
                   s.official_ms AS "officialMs",
                   s.penalty AS "penalty",
                   s.dnf AS "dnf",
                   s.created_at AS "createdAt",
                   MAX(CASE WHEN ss.mode = 'greedy' THEN ss.cross_face_requested END) AS "fastCross",
                   MAX(CASE WHEN ss.mode = 'optimized' THEN ss.cross_face_requested END) AS "optimizedCross"
            FROM solves s
            LEFT JOIN solve_solutions ss
              ON ss.solve_id = s.id AND ss.status = 'ready'
            WHERE s.user_id = :userId
              AND (CAST(:searchTerm AS TEXT) IS NULL
                   OR STRPOS(LOWER(s.scramble), LOWER(CAST(:searchTerm AS TEXT))) > 0)
              AND (CAST(:penalty AS TEXT) = 'all' OR s.penalty = CAST(:penalty AS TEXT))
              AND (
                    (CAST(:timePattern AS TEXT) IS NULL AND CAST(:timeDnf AS BOOLEAN) = FALSE)
                    OR (CAST(:timeDnf AS BOOLEAN) = TRUE AND s.dnf = TRUE)
                    OR (
                        CAST(:timePattern AS TEXT) IS NOT NULL
                        AND s.dnf = FALSE
                        AND (CAST(:timePlusTwoOnly AS BOOLEAN) = FALSE OR s.penalty = '+2')
                        AND (
                            CASE WHEN s.official_ms >= 60000 THEN
                                (s.official_ms / 60000)::TEXT || ':'
                                || LPAD(((s.official_ms / 1000) % 60)::TEXT, 2, '0') || '.'
                                || LPAD(((s.official_ms / 10) % 100)::TEXT, 2, '0')
                            ELSE
                                (s.official_ms / 1000)::TEXT || '.'
                                || LPAD(((s.official_ms / 10) % 100)::TEXT, 2, '0')
                            END
                        ) LIKE CAST(:timePattern AS TEXT)
                    )
                  )
              AND (
                    CAST(:cursorCreatedAt AS TIMESTAMP WITH TIME ZONE) IS NULL
                    OR (s.created_at, s.id) < (
                        CAST(:cursorCreatedAt AS TIMESTAMP WITH TIME ZONE),
                        CAST(:cursorId AS BIGINT)
                    )
                  )
            GROUP BY s.id
            ORDER BY s.created_at DESC, s.id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<HistoryRowProjection> findFilteredHistoryPage(
            @Param("userId") long userId,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("searchTerm") String searchTerm,
            @Param("penalty") String penalty,
            @Param("timePattern") String timePattern,
            @Param("timeDnf") boolean timeDnf,
            @Param("timePlusTwoOnly") boolean timePlusTwoOnly,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT COUNT(*)
            FROM solves s
            WHERE s.user_id = :userId
              AND (CAST(:searchTerm AS TEXT) IS NULL
                   OR STRPOS(LOWER(s.scramble), LOWER(CAST(:searchTerm AS TEXT))) > 0)
              AND (CAST(:penalty AS TEXT) = 'all' OR s.penalty = CAST(:penalty AS TEXT))
              AND (
                    (CAST(:timePattern AS TEXT) IS NULL AND CAST(:timeDnf AS BOOLEAN) = FALSE)
                    OR (CAST(:timeDnf AS BOOLEAN) = TRUE AND s.dnf = TRUE)
                    OR (
                        CAST(:timePattern AS TEXT) IS NOT NULL
                        AND s.dnf = FALSE
                        AND (CAST(:timePlusTwoOnly AS BOOLEAN) = FALSE OR s.penalty = '+2')
                        AND (
                            CASE WHEN s.official_ms >= 60000 THEN
                                (s.official_ms / 60000)::TEXT || ':'
                                || LPAD(((s.official_ms / 1000) % 60)::TEXT, 2, '0') || '.'
                                || LPAD(((s.official_ms / 10) % 100)::TEXT, 2, '0')
                            ELSE
                                (s.official_ms / 1000)::TEXT || '.'
                                || LPAD(((s.official_ms / 10) % 100)::TEXT, 2, '0')
                            END
                        ) LIKE CAST(:timePattern AS TEXT)
                    )
                  )
            """, nativeQuery = true)
    long countFilteredHistory(
            @Param("userId") long userId,
            @Param("searchTerm") String searchTerm,
            @Param("penalty") String penalty,
            @Param("timePattern") String timePattern,
            @Param("timeDnf") boolean timeDnf,
            @Param("timePlusTwoOnly") boolean timePlusTwoOnly
    );

    @Query(value = """
            SELECT id AS "id", official_ms AS "officialMs", dnf AS "dnf", created_at AS "createdAt"
            FROM solves
            WHERE user_id = :userId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TimedSolveProjection> findRecentTimedSolves(
            @Param("userId") long userId,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT COUNT(*)::INTEGER AS "solveCount",
                   COUNT(*) FILTER (WHERE dnf)::INTEGER AS "dnfCount",
                   MIN(official_ms) FILTER (WHERE NOT dnf) AS "bestMs",
                   AVG(official_ms) FILTER (WHERE NOT dnf) AS "averageMs"
            FROM solves
            WHERE user_id = :userId
            """, nativeQuery = true)
    SolveStatisticsAggregateProjection aggregateStatistics(@Param("userId") long userId);
}
