package solver;

import cfop.F2LGeometry.TargetSlot;
import cube.Algorithm;
import cube.CubeOrientation;
import cube.CubeState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import static cfop.F2LGeometry.isTargetCrossSolved;
import static cfop.F2LGeometry.isTargetSlotSolved;
import static cfop.F2LGeometry.targetCrossForOrientation;

/** Package-private optimized F2L search and its bounded-search bookkeeping. */
final class F2LOptimizedSearch {
    private static final int MAX_PATHS_PER_STATE = Integer.getInteger("f2l.optimized.max-paths", 2);
    private static final int MAX_VISITED_STATES = Integer.getInteger("f2l.optimized.max-states", 1_000);
    private static final int MAX_SEARCH_DEPTH = Integer.getInteger("f2l.optimized.max-depth", 8);
    private static final int MAX_TRANSITIONS_PER_STATE = Integer.getInteger("f2l.optimized.max-transitions", 8);
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

        int remainingPairLowerBound(CubeState cube, CubeOrientation orientation);
    }

    private final TargetSlot[] targetSlots;
    private final Consumer<F2LSolver.F2LSearchProgress> progressListener;
    private final BooleanSupplier shouldStop;
    private final Host host;
    private final ToIntFunction<F2LSolver.F2LCandidate> completionEvaluator;
    private int bestContinuationMoves;
    private final Map<F2LStateCodec.CompleteStateKey, List<Algorithm>> pathsByState = new HashMap<>();
    private final Map<F2LStateCodec.CompleteStateKey, List<F2LSolver.F2LCandidate>> completedByState = new LinkedHashMap<>();
    private final Map<F2LStateCodec.CompleteStateKey, Integer> lowerBounds = new HashMap<>();
    private final Map<F2LStateCodec.CompleteStateKey, List<F2LSolver.PhaseSlotSolution>> routeCandidatesByState = new HashMap<>();
    private final Map<TransitionKey, ValidatedTransition> validatedTransitions = new HashMap<>();
    private final PriorityQueue<SearchNode> frontier = new PriorityQueue<>(
            Comparator.comparingInt(SearchNode::estimatedCost)
                    .thenComparing(node -> node.state().solution(), ALGORITHM_COMPARATOR)
    );
    private Algorithm bestAlgorithm;
    private long visitedStates;
    private long prunedStates;
    private long duplicateStates;
    private long generatedTransitions;
    private long rejectedTransitions;
    private int maxFrontierSize;
    private int maxSearchDepth;
    private boolean searchLimitReached;
    private long insertLookupNanos;
    private long setupLookupNanos;
    private long validationNanos;
    private long lastProgressNanos;

    F2LOptimizedSearch(
            TargetSlot[] targetSlots,
            Consumer<F2LSolver.F2LSearchProgress> progressListener,
            Algorithm initialBest,
            int initialContinuationMoves,
            ToIntFunction<F2LSolver.F2LCandidate> completionEvaluator,
            BooleanSupplier shouldStop,
            Host host
    ) {
        this.targetSlots = targetSlots;
        this.progressListener = progressListener;
        this.bestAlgorithm = initialBest.copy();
        this.bestContinuationMoves = initialContinuationMoves;
        this.completionEvaluator = completionEvaluator;
        this.shouldStop = shouldStop;
        this.host = host;
    }

    void search(F2LSolver.OptimizedState state) {
        frontier.clear();
        frontier.add(new SearchNode(state, estimate(state)));
        while (!frontier.isEmpty()) {
            SolveCancellation.throwIfCancelled();
            if (shouldStop.getAsBoolean()) {
                publishProgress();
                return;
            }
            if (visitedStates >= MAX_VISITED_STATES) {
                searchLimitReached = true;
                publishProgress();
                return;
            }

            var node = frontier.remove();
            var current = node.state();
            visitedStates++;
            maxFrontierSize = Math.max(maxFrontierSize, frontier.size());
            maxSearchDepth = Math.max(maxSearchDepth, current.pendingSteps().size());
            publishProgress();

            if (areAllTargetsSolved(current.cube())) {
                var completed = current.solution().copy();
                registerCompletedCandidate(current, completed);
                if (ALGORITHM_COMPARATOR.compare(completed, bestAlgorithm) < 0) {
                    bestAlgorithm = completed.copy();
                } else {
                    prunedStates++;
                }
                continue;
            }

            var stateKey = optimizedStateKey(current);
            if (!registerStatePath(stateKey, current.solution())) {
                duplicateStates++;
                continue;
            }

            var candidates = optimizedCandidates(current);
            generatedTransitions += candidates.size();
            if (candidates.isEmpty()) {
                prunedStates++;
                continue;
            }

            for (var candidate : candidates) {
                SolveCancellation.throwIfCancelled();
                if (shouldStop.getAsBoolean()) {
                    publishProgress();
                    return;
                }
                var nextSolution = current.solution().concat(candidate.algorithm());
                var nextState = new F2LSolver.OptimizedState(
                        candidate.cube(),
                        candidate.orientation(),
                        candidate.protectedSlots(),
                        nextSolution,
                        candidate.pendingSteps()
                );
                if (nextState.pendingSteps().size() > MAX_SEARCH_DEPTH) {
                    rejectedTransitions++;
                    continue;
                }
                int estimatedCost = estimate(nextState);
                SolveCancellation.throwIfCancelled();
                if (shouldStop.getAsBoolean()) {
                    publishProgress();
                    return;
                }
                if (estimatedCost > currentSearchUpperBound()) {
                    rejectedTransitions++;
                    continue;
                }
                frontier.add(new SearchNode(nextState, estimatedCost));
            }
            maxFrontierSize = Math.max(maxFrontierSize, frontier.size());
            publishProgress();
        }
    }

    private boolean registerStatePath(F2LStateCodec.CompleteStateKey stateKey, Algorithm path) {
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
        var candidate = new F2LSolver.F2LCandidate(
                algorithm,
                state.cube().copy(),
                state.orientation().copy(),
                new F2LSolveTrace(
                        algorithm.getMoves(),
                        host.buildPairSteps(state.pendingSteps()),
                        areAllTargetsSolved(state.cube())
                )
        );
        candidates.add(candidate);
        candidates.sort(Comparator.comparing(F2LSolver.F2LCandidate::algorithm, ALGORITHM_COMPARATOR));
        if (candidates.size() > MAX_PATHS_PER_STATE) {
            candidates.remove(candidates.size() - 1);
        }
        completedByState.put(key, List.copyOf(candidates));
        if (completionEvaluator != null && candidates.contains(candidate)) {
            var continuationMoves = completionEvaluator.applyAsInt(candidate);
            if (continuationMoves >= 0) {
                bestContinuationMoves = Math.min(bestContinuationMoves, continuationMoves);
            }
        }
    }

    private List<F2LSolver.OptimizedTransition> optimizedCandidates(F2LSolver.OptimizedState state) {
        var stateKey = optimizedStateKey(state);
        var candidates = routeCandidatesByState.get(stateKey);
        if (candidates == null) {
            var discovered = new ArrayList<F2LSolver.PhaseSlotSolution>();
            var started = System.nanoTime();
            discovered.addAll(host.findInsertDatabaseSlotSolutions(
                    state.cube(), state.orientation(), targetSlots, state.protectedSlots(), this::checkpoint
            ));
            insertLookupNanos += System.nanoTime() - started;
            if (shouldStop.getAsBoolean()) return List.of();
            started = System.nanoTime();
            discovered.addAll(host.findSetupThenInsertDatabaseSlotSolutions(
                    state.cube(), state.orientation(), targetSlots, state.protectedSlots(), this::checkpoint
            ));
            setupLookupNanos += System.nanoTime() - started;
            if (shouldStop.getAsBoolean()) return List.of();
            discovered.sort(PHASE_SOLUTION_COMPARATOR);
            candidates = distinctPhaseSolutions(discovered);
            routeCandidatesByState.put(stateKey, candidates);
        }
        long started = System.nanoTime();
        var completing = slotCompletingTransitions(state, candidates);
        validationNanos += System.nanoTime() - started;
        if (!completing.isEmpty() || shouldStop.getAsBoolean()) {
            return completing;
        }
        var recovery = slotCompletingTransitions(
                state,
                host.findRecoveryThenDatabaseSolutions(state, this::checkpoint)
        );
        var all = new ArrayList<F2LSolver.OptimizedTransition>(completing);
        all.addAll(recovery);
        all.sort(Comparator.comparing(F2LSolver.OptimizedTransition::algorithm, ALGORITHM_COMPARATOR));
        return all.stream().limit(MAX_TRANSITIONS_PER_STATE).toList();
    }

    private List<F2LSolver.OptimizedTransition> slotCompletingTransitions(
            F2LSolver.OptimizedState state,
            List<F2LSolver.PhaseSlotSolution> candidates
    ) {
        var completingByState = new LinkedHashMap<F2LStateCodec.CompleteStateKey, F2LSolver.OptimizedTransition>();
        var sourceKey = optimizedStateKey(state);
        for (var candidate : candidates) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            var transitionKey = new TransitionKey(sourceKey, candidate.targetSlot(), candidate.algorithm().toString());
            var validated = validatedTransitions.get(transitionKey);
            if (validated == null) {
                var trialCube = state.cube().copy();
                var trialOrientation = F2LStateCodec.executeAndReturnOrientation(
                        trialCube, state.orientation(), candidate.algorithm().getMoves()
                );
                if (isTargetSlotSolved(trialCube, candidate.targetSlot())
                        && areProtectedTargetsSolved(
                        trialCube, targetCrossForOrientation(trialOrientation), state.protectedSlots()
                )) {
                    validated = new ValidatedTransition(
                            trialCube.copy(), trialOrientation.copy(),
                            List.copyOf(updateProtectedSlots(trialCube, targetSlots, state.protectedSlots()))
                    );
                    validatedTransitions.put(transitionKey, validated);
                }
            }
            if (validated == null) continue;
            var trialCube = validated.cube().copy();
            var trialOrientation = validated.orientation().copy();
            if (isTargetSlotSolved(trialCube, candidate.targetSlot())
                    && areProtectedTargetsSolved(
                    trialCube,
                    targetCrossForOrientation(trialOrientation),
                    state.protectedSlots()
            )) {
                var nextProtectedSlots = validated.protectedSlots();
                if (nextProtectedSlots.size() <= state.protectedSlots().size()) {
                    continue;
                }
                var key = optimizedResultStateKey(trialCube, trialOrientation, nextProtectedSlots);
                var existing = completingByState.get(key);
                if (existing != null
                        && ALGORITHM_COMPARATOR.compare(candidate.algorithm(), existing.algorithm()) >= 0) {
                    duplicateStates++;
                    continue;
                }
                var pendingStep = host.pendingPairStep(state, candidate, trialCube, trialOrientation);
                var nextPendingSteps = new ArrayList<>(state.pendingSteps());
                nextPendingSteps.add(pendingStep);
                var transition = new F2LSolver.OptimizedTransition(
                        candidate.targetSlot(),
                        candidate.algorithm(),
                        trialCube,
                        trialOrientation,
                        nextProtectedSlots,
                        List.copyOf(nextPendingSteps)
                );
                if (existing != null) duplicateStates++;
                completingByState.put(key, transition);
            }
        }
        var completing = new ArrayList<>(completingByState.values());
        completing.sort(Comparator
                .comparingInt((F2LSolver.OptimizedTransition transition) -> transition.algorithm().getMoveCount()
                        + host.remainingPairLowerBound(transition.cube(), transition.orientation()))
                .thenComparing(F2LSolver.OptimizedTransition::algorithm, ALGORITHM_COMPARATOR)
                .thenComparing(transition -> transition.targetSlot().toString()));
        var selected = new ArrayList<F2LSolver.OptimizedTransition>();
        var selectedSlots = new java.util.HashSet<TargetSlot>();
        for (var transition : completing) {
            if (selectedSlots.add(transition.targetSlot())) selected.add(transition);
        }
        for (var transition : completing) {
            if (selected.size() >= MAX_TRANSITIONS_PER_STATE) break;
            if (!selected.contains(transition)) selected.add(transition);
        }
        return List.copyOf(selected.subList(0, Math.min(MAX_TRANSITIONS_PER_STATE, selected.size())));
    }

    private F2LStateCodec.CompleteStateKey optimizedStateKey(F2LSolver.OptimizedState state) {
        return optimizedResultStateKey(state.cube(), state.orientation(), state.protectedSlots());
    }

    private F2LStateCodec.CompleteStateKey optimizedResultStateKey(
            CubeState cube,
            CubeOrientation orientation,
            List<TargetSlot> protectedSlots
    ) {
        return F2LStateCodec.completeStateKey(cube, orientation, targetSlots, protectedSlots);
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
                -1,
                searchLimitReached
                        ? F2LSolver.SolvePhase.OPTIMIZATION_BUDGET_REACHED
                        : F2LSolver.SolvePhase.F2L_CANDIDATE_GENERATION,
                "",
                0,
                0,
                0,
                0,
                searchLimitReached,
                generatedTransitions,
                rejectedTransitions,
                frontier.size(),
                maxFrontierSize,
                maxSearchDepth,
                searchLimitReached,
                insertLookupNanos,
                setupLookupNanos,
                validationNanos
        ));
    }

    private boolean checkpoint() {
        SolveCancellation.throwIfCancelled();
        publishProgress();
        return shouldStop.getAsBoolean();
    }

    private int estimate(F2LSolver.OptimizedState state) {
        var key = optimizedStateKey(state);
        var remaining = lowerBounds.computeIfAbsent(
                key,
                ignored -> host.remainingPairLowerBound(state.cube(), state.orientation())
        );
        return state.solution().getMoveCount() + remaining;
    }

    private int currentSearchUpperBound() {
        return completionEvaluator == null ? bestAlgorithm.getMoveCount() : bestContinuationMoves;
    }

    private record SearchNode(F2LSolver.OptimizedState state, int estimatedCost) {
    }

    private record TransitionKey(
            F2LStateCodec.CompleteStateKey state,
            TargetSlot targetSlot,
            String algorithm
    ) {
    }

    private record ValidatedTransition(
            CubeState cube,
            CubeOrientation orientation,
            List<TargetSlot> protectedSlots
    ) {
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

    long generatedTransitions() {
        return generatedTransitions;
    }

    long rejectedTransitions() {
        return rejectedTransitions;
    }

    int maxFrontierSize() {
        return maxFrontierSize;
    }

    int maxSearchDepth() {
        return maxSearchDepth;
    }

    boolean searchLimitReached() {
        return searchLimitReached;
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
