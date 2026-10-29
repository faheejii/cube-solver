package server.solve;

import solver.CfopSolveService;

import java.util.List;
import java.util.UUID;

/** Immutable solver and provenance captured before a solve job enters the queue. */
public record SolverSnapshot(
        CfopSolveService solver,
        List<AlgorithmRevisionIdentity> algorithms,
        List<UUID> disabledAlgorithmIds,
        List<UUID> enabledTrialRevisionIds,
        List<String> legacyDisabledAlgorithms,
        String manifestJson
) {
    public SolverSnapshot {
        if (solver == null) throw new IllegalArgumentException("solver cannot be null");
        algorithms = List.copyOf(algorithms == null ? List.of() : algorithms);
        disabledAlgorithmIds = List.copyOf(disabledAlgorithmIds == null ? List.of() : disabledAlgorithmIds);
        enabledTrialRevisionIds = List.copyOf(enabledTrialRevisionIds == null ? List.of() : enabledTrialRevisionIds);
        legacyDisabledAlgorithms = List.copyOf(legacyDisabledAlgorithms == null ? List.of() : legacyDisabledAlgorithms);
    }

    public static SolverSnapshot legacy(CfopSolveService solver, List<String> disabledAlgorithms) {
        return new SolverSnapshot(solver, List.of(), List.of(), List.of(), disabledAlgorithms, null);
    }

    public record AlgorithmRevisionIdentity(
            UUID algorithmId,
            UUID revisionId,
            int revisionNumber,
            String phase,
            String name,
            String status
    ) { }
}
