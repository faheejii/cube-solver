package database.persistence.repository;

import database.persistence.entity.SolveSolutionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public final class SolveSolutionJpaRepositoryImpl implements SolveSolutionUpsertRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void upsert(SolveSolutionEntity solution) {
        entityManager.createNativeQuery("""
                INSERT INTO solve_solutions (
                    solve_id, mode, status, cross_face_requested, cross_face_chosen,
                    solution, normalized_solution, f2l_setup_case_count, f2l_insert_case_count,
                    f2l_trace_json, comparison_json,
                    cross_algorithm, cross_moves, cross_solved, cross_status,
                    f2l_algorithm, f2l_moves, f2l_solved, f2l_status,
                    oll_algorithm, oll_moves, oll_solved, oll_status,
                    pll_algorithm, pll_moves, pll_solved, pll_status,
                    solved_f2l_slots, total_moves, solve_elapsed_ms, fully_solved, solver_version,
                    updated_at
                ) VALUES (
                    :solveId, :mode, :status, :crossFaceRequested, :crossFaceChosen,
                    :solution, :normalizedSolution, :f2lSetupCaseCount, :f2lInsertCaseCount,
                    CAST(:f2lTraceJson AS jsonb), CAST(:comparisonJson AS jsonb),
                    :crossAlgorithm, :crossMoves, :crossSolved, :crossStatus,
                    :f2lAlgorithm, :f2lMoves, :f2lSolved, :f2lStatus,
                    :ollAlgorithm, :ollMoves, :ollSolved, :ollStatus,
                    :pllAlgorithm, :pllMoves, :pllSolved, :pllStatus,
                    :solvedF2LSlots, :totalMoves, :solveElapsedMs, :fullySolved, :solverVersion,
                    NOW()
                )
                ON CONFLICT (solve_id, mode) DO UPDATE SET
                    status = EXCLUDED.status,
                    cross_face_requested = EXCLUDED.cross_face_requested,
                    cross_face_chosen = EXCLUDED.cross_face_chosen,
                    solution = EXCLUDED.solution,
                    normalized_solution = EXCLUDED.normalized_solution,
                    f2l_setup_case_count = EXCLUDED.f2l_setup_case_count,
                    f2l_insert_case_count = EXCLUDED.f2l_insert_case_count,
                    f2l_trace_json = EXCLUDED.f2l_trace_json,
                    comparison_json = EXCLUDED.comparison_json,
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
                """)
                .setParameter("solveId", solution.getSolve().getId())
                .setParameter("mode", solution.getMode())
                .setParameter("status", solution.getStatus())
                .setParameter("crossFaceRequested", solution.getCrossFaceRequested())
                .setParameter("crossFaceChosen", solution.getCrossFaceChosen())
                .setParameter("solution", solution.getSolution())
                .setParameter("normalizedSolution", solution.getNormalizedSolution())
                .setParameter("f2lSetupCaseCount", solution.getF2lSetupCaseCount())
                .setParameter("f2lInsertCaseCount", solution.getF2lInsertCaseCount())
                .setParameter("f2lTraceJson", solution.getF2lTraceJson())
                .setParameter("comparisonJson", solution.getComparisonJson())
                .setParameter("crossAlgorithm", solution.getCrossAlgorithm())
                .setParameter("crossMoves", solution.getCrossMoves())
                .setParameter("crossSolved", solution.isCrossSolved())
                .setParameter("crossStatus", solution.getCrossStatus())
                .setParameter("f2lAlgorithm", solution.getF2lAlgorithm())
                .setParameter("f2lMoves", solution.getF2lMoves())
                .setParameter("f2lSolved", solution.isF2lSolved())
                .setParameter("f2lStatus", solution.getF2lStatus())
                .setParameter("ollAlgorithm", solution.getOllAlgorithm())
                .setParameter("ollMoves", solution.getOllMoves())
                .setParameter("ollSolved", solution.isOllSolved())
                .setParameter("ollStatus", solution.getOllStatus())
                .setParameter("pllAlgorithm", solution.getPllAlgorithm())
                .setParameter("pllMoves", solution.getPllMoves())
                .setParameter("pllSolved", solution.isPllSolved())
                .setParameter("pllStatus", solution.getPllStatus())
                .setParameter("solvedF2LSlots", solution.getSolvedF2LSlots())
                .setParameter("totalMoves", solution.getTotalMoves())
                .setParameter("solveElapsedMs", solution.getSolveElapsedMs())
                .setParameter("fullySolved", solution.isFullySolved())
                .setParameter("solverVersion", solution.getSolverVersion())
                .executeUpdate();
    }
}
