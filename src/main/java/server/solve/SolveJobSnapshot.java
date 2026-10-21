package server.solve;

import solver.CfopSolveResult;

import java.util.List;

/** Immutable public view of asynchronous solve-job progress. */
public record SolveJobSnapshot(
        String id, String status, long statesExplored, long statesPruned, long duplicateStates,
        int bestMoves, int completedCandidates, int candidatesEvaluated, int bestTotalMoves,
        String phase, String currentCrossFace, int completedCrosses, int totalCrosses,
        int optimizationCandidate, int totalOptimizationCandidates, boolean optimizationBudgetExpired,
        CfopSolveResult result, String error, List<String> disabledAlgorithms
) {
    public SolveJobSnapshot(
            String id, String status, long statesExplored, long statesPruned, long duplicateStates,
            int bestMoves, int completedCandidates, int candidatesEvaluated, int bestTotalMoves,
            String phase, String currentCrossFace, int completedCrosses, int totalCrosses,
            int optimizationCandidate, int totalOptimizationCandidates, boolean optimizationBudgetExpired,
            CfopSolveResult result, String error
    ) {
        this(id, status, statesExplored, statesPruned, duplicateStates, bestMoves, completedCandidates,
                candidatesEvaluated, bestTotalMoves, phase, currentCrossFace, completedCrosses, totalCrosses,
                optimizationCandidate, totalOptimizationCandidates, optimizationBudgetExpired, result, error,
                List.of());
    }

    public SolveJobSnapshot {
        disabledAlgorithms = List.copyOf(disabledAlgorithms);
    }
}
