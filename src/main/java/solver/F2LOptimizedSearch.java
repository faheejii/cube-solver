package solver;

import cfop.F2LGeometry.TargetSlot;
import cube.Algorithm;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.Move;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static cfop.F2LGeometry.isTargetCrossSolved;
import static cfop.F2LGeometry.isTargetSlotSolved;
import static cfop.F2LGeometry.targetCrossForOrientation;

/** Package-private optimized F2L search and its bounded-search bookkeeping. */
final class F2LOptimizedSearch {
    private static final int MAX_PATHS_PER_STATE = 3;
    private static final Comparator<Algorithm> ALGORITHM_COMPARATOR = Comparator
            .comparingInt(Algorithm::getMoveCount)
            .thenComparingInt(algorithm -> algorithm.getMoves().size())
            .thenComparing(Algorithm::toString);
    private static final Comparator<Algorithm> RAW_ALGORITHM_COMPARATOR = Comparator
            .comparingInt((Algorithm algorithm) -> algorithm.getMoves().size());
    private static final Comparator<F2LSolver.PhaseSlotSolution> PHASE_SOLUTION_COMPARATOR = Comparator
            .comparing((F2LSolver.PhaseSlotSolution solution) -> solution.algorithm(), ALGORITHM_COMPARATOR)
            .thenComparing(solution -> solution.targetSlot().toString());
    private static final boolean DEBUG_DB = Boolean.getBoolean("f2l.debug");

    interface Host {
        List<F2LSolver.PhaseSlotSolution> findInsertDatabaseSlotSolutions(
                CubeState cube,
                CubeOrientation orientation,
                TargetSlot[] targetSlots,
                List<TargetSlot> protectedSlots,
                F2LSolver.SearchCheckpoint checkpoint
        );

        List<F2LSolver.PhaseSlotSolution> findSetupThenInsertDatabaseSlotSolutions(
                CubeState cube,
                CubeOrientation orientation,
                TargetSlot[] targetSlots,
                List<TargetSlot> protectedSlots,
                F2LSolver.SearchCheckpoint checkpoint
        );

        List<F2LSolver.PhaseSlotSolution> findRecoveryThenDatabaseSolutions(
                F2LSolver.OptimizedState state,
                F2LSolver.SearchCheckpoint checkpoint
        );

        F2LSolver.PendingPairStep pendingPairStep(
                F2LSolver.OptimizedState state,
                F2LSolver.PhaseSlotSolution candidate,
                CubeState trialCube,
                CubeOrientation trialOrientation
        );

        List<F2LPairStep> buildPairSteps(List<F2LSolver.PendingPairStep> pendingSteps);
    }

    private final TargetSlot[] targetSlots;
    private final Consumer<F2LSolver.F2LSearchProgress> progressListener;
    private final BooleanSupplier shouldStop;
    private final Host host;
    private final Map<String, List<Algorithm>> pathsByState = new HashMap<>();
    private final Map<String, List<F2LSolver.F2LCandidate>> completedByState = new LinkedHashMap<>();
    private Algorithm bestAlgorithm;
    private long visitedStates;
    private long prunedStates;
    private long duplicateStates;
    private long lastProgressNanos;

    F2LOptimizedSearch(
            TargetSlot[] targetSlots,
            Consumer<F2LSolver.F2LSearchProgress> progressListener,
            Algorithm initialBest,
            BooleanSupplier shouldStop,
            Host host
    ) {
        this.targetSlots = targetSlots;
        this.progressListener = progressListener;
        this.bestAlgorithm = initialBest.copy();
        this.shouldStop = shouldStop;
        this.host = host;
    }

    void search(F2LSolver.OptimizedState state) {
        SolveCancellation.throwIfCancelled();
        if (shouldStop.getAsBoolean()) {
            publishProgress();
            return;
        }
        visitedStates++;
        publishProgress();

        if (areAllTargetsSolved(state.cube())) {
            var completed = state.solution().copy();
            registerCompletedCandidate(state, completed);
            if (ALGORITHM_COMPARATOR.compare(completed, bestAlgorithm) < 0) {
                bestAlgorithm = completed.copy();
            } else {
                prunedStates++;
            }
            publishProgress();
            return;
        }

        var stateKey = optimizedStateKey(state);
        if (!registerStatePath(stateKey, state.solution())) {
            duplicateStates++;
            publishProgress();
            return;
        }

        var candidates = optimizedCandidates(state);
        if (candidates.isEmpty()) {
            prunedStates++;
            publishProgress();
            return;
        }

        for (var candidate : candidates) {
            var nextSolution = state.solution().concat(candidate.algorithm());
            search(new F2LSolver.OptimizedState(
                    candidate.cube(),
                    candidate.orientation(),
                    candidate.protectedSlots(),
                    nextSolution,
                    candidate.pendingSteps()
            ));
        }
        publishProgress();
    }

    private boolean registerStatePath(String stateKey, Algorithm path) {
        var paths = new ArrayList<>(pathsByState.getOrDefault(stateKey, List.of()));
        if (paths.stream().anyMatch(existing -> existing.toString().equals(path.toString()))) {
            return false;
        }
        paths.add(path);
        paths.sort(ALGORITHM_COMPARATOR);
        if (paths.size() > MAX_PATHS_PER_STATE) {
            var removed = paths.remove(paths.size() - 1);
            if (removed == path) {
                return false;
            }
        }
        pathsByState.put(stateKey, List.copyOf(paths));
        return true;
    }

    private void registerCompletedCandidate(F2LSolver.OptimizedState state, Algorithm algorithm) {
        var key = optimizedResultStateKey(state.cube(), state.orientation(), state.protectedSlots());
        var candidates = new ArrayList<>(completedByState.getOrDefault(key, List.of()));
        if (candidates.stream().anyMatch(existing -> existing.algorithm().toString().equals(algorithm.toString()))) {
            duplicateStates++;
            return;
        }
        candidates.add(new F2LSolver.F2LCandidate(
                algorithm,
                state.cube().copy(),
                state.orientation().copy(),
                new F2LSolveTrace(
                        algorithm.getMoves(),
                        host.buildPairSteps(state.pendingSteps()),
                        areAllTargetsSolved(state.cube())
                )
        ));
        candidates.sort(Comparator.comparing(F2LSolver.F2LCandidate::algorithm, ALGORITHM_COMPARATOR));
        if (candidates.size() > MAX_PATHS_PER_STATE) {
            candidates.remove(candidates.size() - 1);
        }
        completedByState.put(key, List.copyOf(candidates));
    }

    private List<F2LSolver.OptimizedTransition> optimizedCandidates(F2LSolver.OptimizedState state) {
        var candidates = new ArrayList<F2LSolver.PhaseSlotSolution>();
        candidates.addAll(host.findInsertDatabaseSlotSolutions(
                state.cube(),
                state.orientation(),
                targetSlots,
                state.protectedSlots(),
                this::checkpoint
        ));
        if (shouldStop.getAsBoolean()) {
            return List.of();
        }
        candidates.addAll(host.findSetupThenInsertDatabaseSlotSolutions(
                state.cube(),
                state.orientation(),
                targetSlots,
                state.protectedSlots(),
                this::checkpoint
        ));
        candidates.sort(PHASE_SOLUTION_COMPARATOR);
        var completing = slotCompletingTransitions(state, distinctPhaseSolutions(candidates));
        if (!completing.isEmpty() || shouldStop.getAsBoolean()) {
            return completing;
        }
        return slotCompletingTransitions(
                state,
                host.findRecoveryThenDatabaseSolutions(state, this::checkpoint)
        );
    }

    private List<F2LSolver.OptimizedTransition> slotCompletingTransitions(
            F2LSolver.OptimizedState state,
            List<F2LSolver.PhaseSlotSolution> candidates
    ) {
        var completingByState = new LinkedHashMap<String, F2LSolver.OptimizedTransition>();
        for (var candidate : candidates) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            var trialCube = state.cube().copy();
            var trialOrientation = F2LStateCodec.executeAndReturnOrientation(
                    trialCube,
                    state.orientation(),
                    candidate.algorithm().getMoves()
            );
            if (isTargetSlotSolved(trialCube, candidate.targetSlot())
                    && areProtectedTargetsSolved(
                    trialCube,
                    targetCrossForOrientation(trialOrientation),
                    state.protectedSlots()
            )) {
                var nextProtectedSlots = updateProtectedSlots(
                        trialCube,
                        targetSlots,
                        state.protectedSlots()
                );
                if (nextProtectedSlots.size() <= state.protectedSlots().size()) {
                    continue;
                }
                var pendingStep = host.pendingPairStep(state, candidate, trialCube, trialOrientation);
                var nextPendingSteps = new ArrayList<>(state.pendingSteps());
                nextPendingSteps.add(pendingStep);
                var transition = new F2LSolver.OptimizedTransition(
                        candidate.algorithm(),
                        trialCube,
                        trialOrientation,
                        nextProtectedSlots,
                        List.copyOf(nextPendingSteps)
                );
                var key = optimizedResultStateKey(trialCube, trialOrientation, nextProtectedSlots);
                var existing = completingByState.get(key);
                if (existing == null
                        || ALGORITHM_COMPARATOR.compare(transition.algorithm(), existing.algorithm()) < 0) {
                    if (existing != null) {
                        duplicateStates++;
                    }
                    completingByState.put(key, transition);
                } else {
                    duplicateStates++;
                }
            }
        }
        var completing = new ArrayList<>(completingByState.values());
        completing.sort(Comparator.comparing(F2LSolver.OptimizedTransition::algorithm, ALGORITHM_COMPARATOR));
        return List.copyOf(completing);
    }

    private String optimizedStateKey(F2LSolver.OptimizedState state) {
        return optimizedResultStateKey(state.cube(), state.orientation(), state.protectedSlots());
    }

    private String optimizedResultStateKey(
            CubeState cube,
            CubeOrientation orientation,
            List<TargetSlot> protectedSlots
    ) {
        var protectedFlags = new StringBuilder();
        for (var targetSlot : targetSlots) {
            protectedFlags.append(protectedSlots.contains(targetSlot) ? '1' : '0');
        }
        return Arrays.toString(cube.cornerPerm)
                + Arrays.toString(cube.cornerOri)
                + Arrays.toString(cube.edgePerm)
                + Arrays.toString(cube.edgeOri)
                + "|U=" + orientation.faceAt(Face.U)
                + "|R=" + orientation.faceAt(Face.R)
                + "|F=" + orientation.faceAt(Face.F)
                + "|slots=" + protectedFlags;
    }

    private void publishProgress() {
        var now = System.nanoTime();
        if (lastProgressNanos != 0
                && now - lastProgressNanos < 100_000_000L
                && visitedStates % 1_024 != 0) {
            return;
        }
        lastProgressNanos = now;
        progressListener.accept(new F2LSolver.F2LSearchProgress(
                visitedStates,
                prunedStates,
                duplicateStates,
                bestAlgorithm.getMoveCount(),
                completedByState.size(),
                0,
                -1
        ));
    }

    private boolean checkpoint() {
        SolveCancellation.throwIfCancelled();
        publishProgress();
        return shouldStop.getAsBoolean();
    }

    List<F2LSolver.F2LCandidate> completedCandidates() {
        return completedByState.values().stream()
                .map(candidates -> candidates.get(0))
                .sorted(Comparator.comparing(F2LSolver.F2LCandidate::algorithm, ALGORITHM_COMPARATOR))
                .toList();
    }

    long visitedStates() {
        return visitedStates;
    }

    long prunedStates() {
        return prunedStates;
    }

    long duplicateStates() {
        return duplicateStates;
    }

    private boolean areAllTargetsSolved(CubeState cube) {
        for (var targetSlot : targetSlots) {
            if (!isTargetSlotSolved(cube, targetSlot)) {
                return false;
            }
        }
        return true;
    }

    private static List<TargetSlot> updateProtectedSlots(
            CubeState cube,
            TargetSlot[] targetSlots,
            List<TargetSlot> current
    ) {
        var updated = new ArrayList<TargetSlot>(current);
        for (var targetSlot : targetSlots) {
            if (!updated.contains(targetSlot) && isTargetSlotSolved(cube, targetSlot)) {
                updated.add(targetSlot);
            }
        }
        return updated;
    }

    private static boolean areProtectedTargetsSolved(
            CubeState cube,
            cube.Edge[] targetCross,
            List<TargetSlot> protectedSlots
    ) {
        if (!isTargetCrossSolved(cube, targetCross)) {
            return false;
        }
        for (var slot : protectedSlots) {
            if (!isTargetSlotSolved(cube, slot)) {
                return false;
            }
        }
        return true;
    }

    private static List<F2LSolver.PhaseSlotSolution> distinctPhaseSolutions(
            List<F2LSolver.PhaseSlotSolution> solutions
    ) {
        var distinct = new LinkedHashMap<String, F2LSolver.PhaseSlotSolution>();
        for (var solution : solutions) {
            distinct.putIfAbsent(solution.targetSlot() + "|" + solution.algorithm(), solution);
        }
        return List.copyOf(distinct.values());
    }
}
