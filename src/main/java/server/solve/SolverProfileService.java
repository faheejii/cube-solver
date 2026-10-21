package server.solve;

import algorithms.AlgorithmCaseCatalog;
import solver.CfopSolveService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Resolves immutable solver contexts for validated algorithm-disable profiles. */
public final class SolverProfileService {
    private static final int MAX_CACHED_PROFILES = 16;

    private final CfopSolveService defaultSolver;
    private final LinkedHashMap<List<String>, CfopSolveService> solvers = new LinkedHashMap<>(16, 0.75f, true);
    private volatile Set<String> canonicalIds;

    public SolverProfileService(CfopSolveService defaultSolver) {
        this.defaultSolver = java.util.Objects.requireNonNull(defaultSolver, "defaultSolver");
        solvers.put(List.of(), defaultSolver);
    }

    public List<String> validate(Collection<String> disabledIds) {
        if (disabledIds == null) {
            return List.of();
        }
        var normalized = new ArrayList<String>();
        for (var id : disabledIds) {
            if (id == null || !canonicalIds().contains(id)) {
                throw new IllegalArgumentException("Unknown or non-canonical algorithm: " + id);
            }
            if (normalized.contains(id)) {
                throw new IllegalArgumentException("Duplicate disabled algorithm: " + id);
            }
            normalized.add(id);
        }
        normalized.sort(String::compareTo);
        return List.copyOf(normalized);
    }

    private Set<String> canonicalIds() {
        var current = canonicalIds;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (canonicalIds == null) {
                canonicalIds = AlgorithmCaseCatalog.canonicalIds();
            }
            return canonicalIds;
        }
    }

    public synchronized CfopSolveService solverFor(Collection<String> disabledIds) {
        var profile = validate(disabledIds);
        var existing = solvers.get(profile);
        if (existing != null) {
            return existing;
        }
        var disabled = Set.copyOf(profile);
        var created = new CfopSolveService(
                AlgorithmCaseCatalog.setupDatabase(disabled),
                AlgorithmCaseCatalog.insertDatabase(disabled),
                AlgorithmCaseCatalog.ollDatabase(disabled),
                AlgorithmCaseCatalog.pllDatabase(disabled)
        );
        solvers.put(profile, created);
        while (solvers.size() > MAX_CACHED_PROFILES) {
            var eldest = solvers.keySet().iterator().next();
            if (eldest.isEmpty() && solvers.size() > 1) {
                var defaultProfile = eldest;
                solvers.remove(defaultProfile);
                solvers.put(defaultProfile, defaultSolver);
            } else {
                solvers.remove(eldest);
            }
        }
        return created;
    }
}
