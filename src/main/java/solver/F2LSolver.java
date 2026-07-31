package solver;

import algorithms.F2LInsertCaseDatabase;
import algorithms.F2LSetupCaseDatabase;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry.SlotPair;
import cfop.F2LGeometry.TargetSlot;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientationFrames;
import cube.OrientedCube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.LongAdder;

import static cfop.F2LGeometry.ensureCrossSolved;
import static cfop.F2LGeometry.isPairConnected;
import static cfop.F2LGeometry.isTargetCrossSolved;
import static cfop.F2LGeometry.isTargetSlotSolved;
import static cfop.F2LGeometry.targetCrossForOrientation;
import static cfop.F2LGeometry.targetSlotFor;
import static cfop.F2LGeometry.targetSlotsForOrientation;
import static cfop.F2LGeometry.visibleSlotForTarget;

public class F2LSolver {
    private static final int MAX_PATHS_PER_STATE = 3;
    private static final boolean DEBUG_DB = Boolean.getBoolean("f2l.debug");
    private static final boolean DEBUG_VERBOSE = Boolean.getBoolean("f2l.debug.verbose");
    private static final Comparator<Algorithm> ALGORITHM_COMPARATOR = Comparator
            .comparingInt(Algorithm::getMoveCount)
            .thenComparingInt(algorithm -> algorithm.getMoves().size())
            .thenComparing(Algorithm::toString);
    private static final Comparator<Algorithm> RAW_ALGORITHM_COMPARATOR = Comparator
            .comparingInt((Algorithm algorithm) -> algorithm.getMoves().size());
    private static final Comparator<PhaseSlotSolution> PHASE_SOLUTION_COMPARATOR = Comparator
            .comparing((PhaseSlotSolution solution) -> solution.algorithm(), ALGORITHM_COMPARATOR)
            .thenComparing(solution -> solution.targetSlot().toString());
    private static final Comparator<PhaseSlotSolution> RAW_PHASE_SOLUTION_COMPARATOR = Comparator
            .comparing((PhaseSlotSolution solution) -> solution.algorithm(), RAW_ALGORITHM_COMPARATOR);

    private static final Algorithm[] DB_PREFIX_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y)),
            Algorithm.fromMoves(List.of(Move.Y, Move.U)),
            Algorithm.fromMoves(List.of(Move.Y, Move.U2)),
            Algorithm.fromMoves(List.of(Move.Y, Move.U_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME, Move.U)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME, Move.U2)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME, Move.U_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y2)),
            Algorithm.fromMoves(List.of(Move.Y2, Move.U)),
            Algorithm.fromMoves(List.of(Move.Y2, Move.U2)),
            Algorithm.fromMoves(List.of(Move.Y2, Move.U_PRIME))
    };
    private final F2LSetupCaseDatabase setupCaseDatabase;
    private final F2LInsertCaseDatabase insertCaseDatabase;
    private final LongAdder setupDatabaseMisses = new LongAdder();
    private final LongAdder insertDatabaseMisses = new LongAdder();

    public F2LSolver() {
        this(F2LSetupCaseDatabase.seedCases(), F2LInsertCaseDatabase.seedCases());
    }

    public F2LSolver(F2LSetupCaseDatabase setupCaseDatabase, F2LInsertCaseDatabase insertCaseDatabase) {
        this.setupCaseDatabase = setupCaseDatabase == null ? F2LSetupCaseDatabase.empty() : setupCaseDatabase;
        this.insertCaseDatabase = insertCaseDatabase == null ? F2LInsertCaseDatabase.empty() : insertCaseDatabase;
        this.setupCaseDatabase.validate();
        this.insertCaseDatabase.validate();
    }

    public F2LDiagnostics diagnostics() {
        return new F2LDiagnostics(
                setupDatabaseMisses.sum(),
                insertDatabaseMisses.sum()
        );
    }

    public Algorithm solve(CubeState cube) {
        return solveStage(cube.copy(), new CubeOrientation());
    }

    public Algorithm solveAfterCross(CubeState cube, Face crossFace) {
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveStage(cube.copy(), OrientationFrames.orientedFrameFor(crossFace)));
    }

    public Algorithm solve(OrientedCube cube) {
        return solveStage(cube.cubeState().copy(), cube.orientation());
    }

    public Algorithm solveOptimized(OrientedCube cube) {
        return solveOptimizedWithProgress(cube, ignored -> {
        });
    }

    public Algorithm solveOptimized(OrientedCube cube, LongConsumer progressListener) {
        return solveOptimizedWithProgress(
                cube,
                progress -> progressListener.accept(progress.statesExplored())
        );
    }

    public Algorithm solveOptimizedWithProgress(
            OrientedCube cube,
            Consumer<F2LSearchProgress> progressListener
    ) {
        var greedyUpperBound = solve(cube);
        var candidates = solveOptimizedCandidates(cube, progressListener, greedyUpperBound);
        if (candidates.isEmpty()) {
            return greedyUpperBound;
        }
        return candidates.stream()
                .map(F2LCandidate::algorithm)
                .min(ALGORITHM_COMPARATOR)
                .orElseThrow();
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveStage(cube.copy(), OrientationFrames.orientedFrameFor(crossFace)));
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot) {
        return solveSlot(cube, slot, Face.D);
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot, Face crossFace) {
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        return OrientationFrames.orientationToD(crossFace).concat(
                solveSlotInternal(
                        cube.copy(),
                        orientation,
                        targetCrossForOrientation(orientation),
                        targetSlotFor(slot, orientation),
                        List.of()
                )
        );
    }

    private Algorithm solveForTargets(CubeState cube, CubeOrientation orientation) {
        var targetSlots = targetSlotsForOrientation(orientation);
        var workingCube = cube.copy();
        var currentOrientation = orientation.copy();
        ensureCrossSolved(workingCube, targetCrossForOrientation(currentOrientation));

        var solution = new Algorithm();
        var protectedSlots = new ArrayList<TargetSlot>();
        for (var targetSlot : targetSlots) {
            if (isTargetSlotSolved(workingCube, targetSlot)) {
                protectedSlots.add(targetSlot);
            }
        }

        while (true) {
            SolveCancellation.throwIfCancelled();
            var fallbackTarget = firstUnsolvedTarget(workingCube, targetSlots, protectedSlots);
            if (fallbackTarget.isEmpty()) {
                break;
            }
            var insertSolution = findBestInsertDatabaseSlotSolution(workingCube, currentOrientation, targetSlots, protectedSlots);
            if (insertSolution.isPresent()) {
                var candidate = insertSolution.get();
                if (DEBUG_DB) {
                    System.out.println("[F2L STAGE] insert DB selected targetSlot=" + candidate.targetSlot()
                            + " algorithm=" + candidate.algorithm());
                }
                currentOrientation = executeAndReturnOrientation(workingCube, currentOrientation, candidate.algorithm().getMoves());
                solution = solution.concat(candidate.algorithm());
                ensureTargetSlotSolved(workingCube, candidate.targetSlot(), "insert database");
                if (!protectedSlots.contains(candidate.targetSlot())) {
                    protectedSlots.add(candidate.targetSlot());
                }
                continue;
            }

            var setupThenInsertSolution = findBestSetupThenInsertDatabaseSlotSolution(
                    workingCube,
                    currentOrientation,
                    targetSlots,
                    protectedSlots
            );
            if (setupThenInsertSolution.isPresent()) {
                var candidate = setupThenInsertSolution.get();
                if (DEBUG_DB) {
                    System.out.println("[F2L STAGE] setup+insert DB selected targetSlot=" + candidate.targetSlot()
                            + " algorithm=" + candidate.algorithm());
                }
                currentOrientation = executeAndReturnOrientation(workingCube, currentOrientation, candidate.algorithm().getMoves());
                solution = solution.concat(candidate.algorithm());
                ensureTargetSlotSolved(workingCube, candidate.targetSlot(), "setup+insert database");
                if (!protectedSlots.contains(candidate.targetSlot())) {
                    protectedSlots.add(candidate.targetSlot());
                }
                continue;
            }

            var targetCross = targetCrossForOrientation(currentOrientation);
            var targetSlot = fallbackTarget.get();
            var slotSolution = solveSlotInternal(workingCube, currentOrientation, targetCross, targetSlot, protectedSlots);
            currentOrientation = executeAndReturnOrientation(workingCube, currentOrientation, slotSolution.getMoves());
            solution = solution.concat(slotSolution);
            ensureTargetSlotSolved(workingCube, targetSlot, "fallback search");
            if (!protectedSlots.contains(targetSlot)) {
                protectedSlots.add(targetSlot);
            }
        }

        return Algorithm.normalize(solution);
    }

    private static void ensureTargetSlotSolved(CubeState cube, TargetSlot targetSlot, String source) {
        if (!isTargetSlotSolved(cube, targetSlot)) {
            throw new IllegalStateException(source + " did not solve target slot " + targetSlot);
        }
    }

    public List<F2LCandidate> solveOptimizedCandidates(
            OrientedCube cube,
            Consumer<F2LSearchProgress> progressListener
    ) {
        return solveOptimizedCandidates(cube, progressListener, null);
    }

    public List<F2LCandidate> solveOptimizedCandidates(
            OrientedCube cube,
            Consumer<F2LSearchProgress> progressListener,
            Algorithm greedyUpperBound
    ) {
        return solveOptimizedCandidates(cube, progressListener, greedyUpperBound, () -> false);
    }

    /**
     * Returns the complete candidates found before {@code shouldStop} becomes true.
     * A stop is a normal bounded-search result, not a solve cancellation.
     */
    public List<F2LCandidate> solveOptimizedCandidates(
            OrientedCube cube,
            Consumer<F2LSearchProgress> progressListener,
            Algorithm greedyUpperBound,
            BooleanSupplier shouldStop
    ) {
        var initialCube = cube.cubeState().copy();
        var initialOrientation = cube.orientation();
        var targetSlots = targetSlotsForOrientation(initialOrientation);
        var protectedSlots = initiallyProtectedSlots(initialCube, targetSlots);
        ensureCrossSolved(initialCube, targetCrossForOrientation(initialOrientation));

        var effectiveGreedyUpperBound = greedyUpperBound == null
                ? solveForTargets(initialCube.copy(), initialOrientation.copy())
                : greedyUpperBound.copy();
        var search = new OptimizedSearch(
                targetSlots,
                progressListener == null ? ignored -> {
                } : progressListener,
                effectiveGreedyUpperBound,
                shouldStop == null ? () -> false : shouldStop
        );
        search.search(new OptimizedState(
                initialCube,
                initialOrientation.copy(),
                protectedSlots,
                new Algorithm()
        ));

        if (DEBUG_DB) {
            System.out.println("[F2L OPTIMIZED] visited=" + search.visitedStates()
                    + " pruned=" + search.prunedStates()
                    + " duplicates=" + search.duplicateStates()
                    + " completed=" + search.completedCandidates().size());
        }

        return search.completedCandidates();
    }

    private static List<TargetSlot> initiallyProtectedSlots(CubeState cube, TargetSlot[] targetSlots) {
        var protectedSlots = new ArrayList<TargetSlot>();
        for (var targetSlot : targetSlots) {
            if (isTargetSlotSolved(cube, targetSlot)) {
                protectedSlots.add(targetSlot);
            }
        }
        return protectedSlots;
    }

    private static List<TargetSlot> updateProtectedSlots(CubeState cube, TargetSlot[] targetSlots, List<TargetSlot> current) {
        var updated = new ArrayList<TargetSlot>(current);
        for (var targetSlot : targetSlots) {
            if (!updated.contains(targetSlot) && isTargetSlotSolved(cube, targetSlot)) {
                updated.add(targetSlot);
            }
        }
        return updated;
    }

    private static Optional<TargetSlot> firstUnsolvedTarget(
            CubeState cube,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots
    ) {
        for (var targetSlot : targetSlots) {
            if (!protectedSlots.contains(targetSlot) && !isTargetSlotSolved(cube, targetSlot)) {
                return Optional.of(targetSlot);
            }
        }
        return Optional.empty();
    }

    private Optional<PhaseSlotSolution> findBestInsertDatabaseSlotSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var targetSlot : targetSlots) {
            SolveCancellation.throwIfCancelled();
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            var solution = findPrefixedInsertDatabaseSolution(cube, orientation, targetSlot, protectedSlots);
            solution.map(algorithm -> new PhaseSlotSolution(targetSlot, algorithm))
                    .ifPresent(candidates::add);
        }
        candidates.sort(Comparator.comparingInt(candidate -> candidate.algorithm().getMoves().size()));
        return candidates.stream().findFirst();
    }

    private List<PhaseSlotSolution> findInsertDatabaseSlotSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots,
            SearchCheckpoint checkpoint
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var targetSlot : targetSlots) {
            if (checkpoint.shouldStop()) {
                break;
            }
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            for (var algorithm : findPrefixedInsertDatabaseSolutions(
                    cube, orientation, targetSlot, protectedSlots, true, checkpoint
            )) {
                candidates.add(new PhaseSlotSolution(targetSlot, algorithm));
            }
        }
        candidates.sort(RAW_PHASE_SOLUTION_COMPARATOR);
        return distinctPhaseSolutions(candidates);
    }

    private Optional<PhaseSlotSolution> findBestSetupThenInsertDatabaseSlotSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var targetSlot : targetSlots) {
            SolveCancellation.throwIfCancelled();
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            findPrefixedSetupThenInsertDatabaseSolution(cube, orientation, targetSlot, protectedSlots)
                    .map(algorithm -> new PhaseSlotSolution(targetSlot, algorithm))
                    .ifPresent(candidates::add);
        }
        candidates.sort(Comparator.comparingInt(candidate -> candidate.algorithm().getMoves().size()));
        return candidates.stream().findFirst();
    }

    private List<PhaseSlotSolution> findSetupThenInsertDatabaseSlotSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots,
            SearchCheckpoint checkpoint
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var targetSlot : targetSlots) {
            if (checkpoint.shouldStop()) {
                break;
            }
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            for (var algorithm : findPrefixedSetupThenInsertDatabaseSolutions(
                    cube, orientation, targetSlot, protectedSlots, checkpoint
            )) {
                candidates.add(new PhaseSlotSolution(targetSlot, algorithm));
            }
        }
        candidates.sort(RAW_PHASE_SOLUTION_COMPARATOR);
        return distinctPhaseSolutions(candidates);
    }

    private Optional<Algorithm> findPrefixedSetupThenInsertDatabaseSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        if (setupCaseDatabase.size() == 0 || insertCaseDatabase.size() == 0) {
            return Optional.empty();
        }

        var candidates = new ArrayList<Algorithm>();
        for (var setup : findPrefixedSetupDatabaseSolutions(cube, orientation, targetSlot, protectedSlots)) {
            SolveCancellation.throwIfCancelled();
            var setupCube = cube.copy();
            var setupOrientation = executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
            findPrefixedInsertDatabaseSolution(setupCube, setupOrientation, targetSlot, protectedSlots, false)
                    .ifPresent(insert -> candidates.add(Algorithm.normalize(setup.concat(insert))));
        }

        for (var setup : findPrefixedValidatedSetupAlgorithms(cube, orientation, targetSlot, protectedSlots)) {
            SolveCancellation.throwIfCancelled();
            var setupCube = cube.copy();
            var setupOrientation = executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
            findPrefixedInsertDatabaseSolution(setupCube, setupOrientation, targetSlot, protectedSlots, false)
                    .ifPresent(insert -> candidates.add(Algorithm.normalize(setup.concat(insert))));
        }

        candidates.sort(Comparator.comparingInt(algorithm -> algorithm.getMoves().size()));
        return candidates.stream().findFirst();
    }

    private List<Algorithm> findPrefixedSetupThenInsertDatabaseSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            SearchCheckpoint checkpoint
    ) {
        if (setupCaseDatabase.size() == 0 || insertCaseDatabase.size() == 0) {
            return List.of();
        }

        var candidates = new ArrayList<Algorithm>();
        for (var setup : findPrefixedSetupDatabaseSolutions(cube, orientation, targetSlot, protectedSlots, checkpoint)) {
            if (checkpoint.shouldStop()) {
                break;
            }
            var setupCube = cube.copy();
            var setupOrientation = executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
            for (var insert : findPrefixedInsertDatabaseSolutions(
                    setupCube, setupOrientation, targetSlot, protectedSlots, false, checkpoint
            )) {
                candidates.add(Algorithm.normalize(setup.concat(insert)));
            }
        }

        candidates.sort(RAW_ALGORITHM_COMPARATOR);
        return distinctAlgorithms(candidates);
    }

    private Algorithm solveStage(CubeState cube, CubeOrientation initialOrientation) {
        return solveForTargets(
                cube,
                initialOrientation
        );
    }

    private Algorithm solveSlotInternal(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        ensureCrossSolved(cube, targetCross);
        if (areGoalsSolved(cube, targetCross, targetSlot, protectedSlots)) {
            return new Algorithm();
        }

        var targetPair = new SlotPair(targetSlot.corner(), targetSlot.edge());
        var directInsert = findPrefixedInsertDatabaseSolution(cube, orientation, targetSlot, protectedSlots);
        if (directInsert.isPresent()) {
            return directInsert.get();
        }

        var setup = solveSetupPhase(cube, orientation, targetCross, targetPair, targetSlot, protectedSlots);
        var setupCube = cube.copy();
        var setupOrientation = executeAndReturnOrientation(setupCube, orientation, setup.getMoves());

        var insert = solveInsertPhase(
                setupCube,
                setupOrientation,
                targetCrossForOrientation(setupOrientation),
                targetSlot,
                protectedSlots
        );
        return Algorithm.normalize(setup.concat(insert));
    }

    private Algorithm solveSetupPhase(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            SlotPair targetPair,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        if (isSetupGoalSolved(cube, orientation, targetCross, targetPair, protectedSlots)) {
            return new Algorithm();
        }
        var databaseSolution = findPrefixedSetupDatabaseSolution(cube, orientation, targetSlot, protectedSlots);
        if (databaseSolution.isPresent()) {
            return databaseSolution.get();
        }

        handleDatabaseMiss(databaseMissContext(
                F2LDatabaseMissContext.Phase.SETUP,
                cube,
                orientation,
                targetSlot,
                protectedSlots
        ));

        throw new F2LDatabaseMissException(databaseMissContext(
                F2LDatabaseMissContext.Phase.SETUP,
                cube,
                orientation,
                targetSlot,
                protectedSlots
        ));
    }

    private Algorithm solveInsertPhase(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        if (areGoalsSolved(cube, targetCross, targetSlot, protectedSlots)) {
            return new Algorithm();
        }
        var databaseSolution = findPrefixedInsertDatabaseSolution(cube, orientation, targetSlot, protectedSlots);
        if (databaseSolution.isPresent()) {
            return databaseSolution.get();
        }

        handleDatabaseMiss(databaseMissContext(
                F2LDatabaseMissContext.Phase.INSERT,
                cube,
                orientation,
                targetSlot,
                protectedSlots
        ));

        throw new F2LDatabaseMissException(databaseMissContext(
                F2LDatabaseMissContext.Phase.INSERT,
                cube,
                orientation,
                targetSlot,
                protectedSlots
        ));
    }

    private Optional<Algorithm> findPrefixedSetupDatabaseSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        var candidates = findPrefixedSetupDatabaseSolutions(cube, orientation, targetSlot, protectedSlots);
        return candidates.stream().findFirst();
    }

    private List<Algorithm> findPrefixedSetupDatabaseSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        return findPrefixedSetupDatabaseSolutions(cube, orientation, targetSlot, protectedSlots, SearchCheckpoint.NEVER);
    }

    private List<Algorithm> findPrefixedSetupDatabaseSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            SearchCheckpoint checkpoint
    ) {
        var candidates = new ArrayList<Algorithm>();
        if (setupCaseDatabase.size() == 0) {
            return candidates;
        }

        for (var prefix : DB_PREFIX_TRIALS) {
            SolveCancellation.throwIfCancelled();
            if (checkpoint.shouldStop()) {
                break;
            }
            var prefixedCube = cube.copy();
            var prefixedOrientation = executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
            var match = findSetupDatabaseSolution(
                    prefixedCube,
                    prefixedOrientation,
                    targetSlot,
                    protectedSlots
            );
            match.ifPresent(algorithm -> candidates.add(Algorithm.normalize(prefix.concat(algorithm))));
        }

        candidates.sort(RAW_ALGORITHM_COMPARATOR);
        return distinctAlgorithms(candidates);
    }

    private List<Algorithm> findPrefixedValidatedSetupAlgorithms(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        var candidates = new ArrayList<Algorithm>();
        if (setupCaseDatabase.size() == 0) {
            return candidates;
        }

        for (var prefix : DB_PREFIX_TRIALS) {
            SolveCancellation.throwIfCancelled();
            var prefixedCube = cube.copy();
            var prefixedOrientation = executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
            var preservedMask = preservationMaskFor(protectedSlots, prefixedOrientation);

            for (var setupCase : setupCaseDatabase.allCases()) {
                SolveCancellation.throwIfCancelled();
                if (!setupCase.preservedSlots().preservesAll(preservedMask)) {
                    continue;
                }

                var setup = Algorithm.normalize(prefix.concat(setupCase.algorithm()));
                var trialCube = cube.copy();
                var resultingOrientation = executeAndReturnOrientation(trialCube, orientation, setup.getMoves());
                if (areProtectedTargetsSolved(
                        trialCube,
                        targetCrossForOrientation(resultingOrientation),
                        protectedSlots
                )) {
                    candidates.add(setup);
                }
            }
        }

        candidates.sort(RAW_ALGORITHM_COMPARATOR);
        return distinctAlgorithms(candidates);
    }

    private Optional<Algorithm> findPrefixedInsertDatabaseSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        return findPrefixedInsertDatabaseSolution(cube, orientation, targetSlot, protectedSlots, true);
    }

    private Optional<Algorithm> findPrefixedInsertDatabaseSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            boolean logMatch
    ) {
        if (insertCaseDatabase.size() == 0) {
            return Optional.empty();
        }

        var candidates = new ArrayList<Algorithm>();
        for (var prefix : DB_PREFIX_TRIALS) {
            SolveCancellation.throwIfCancelled();
            var prefixedCube = cube.copy();
            var prefixedOrientation = executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
            var match = findInsertDatabaseSolution(
                    prefixedCube,
                    prefixedOrientation,
                    targetSlot,
                    protectedSlots,
                    logMatch
            );
            match.ifPresent(algorithm -> candidates.add(Algorithm.normalize(prefix.concat(algorithm))));
        }

        candidates.sort(Comparator.comparingInt(algorithm -> algorithm.getMoves().size()));
        return candidates.stream().findFirst();
    }

    private List<Algorithm> findPrefixedInsertDatabaseSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            boolean logMatch
    ) {
        return findPrefixedInsertDatabaseSolutions(
                cube, orientation, targetSlot, protectedSlots, logMatch, SearchCheckpoint.NEVER
        );
    }

    private List<Algorithm> findPrefixedInsertDatabaseSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            boolean logMatch,
            SearchCheckpoint checkpoint
    ) {
        if (insertCaseDatabase.size() == 0) {
            return List.of();
        }

        var candidates = new ArrayList<Algorithm>();
        for (var prefix : DB_PREFIX_TRIALS) {
            SolveCancellation.throwIfCancelled();
            if (checkpoint.shouldStop()) {
                break;
            }
            var prefixedCube = cube.copy();
            var prefixedOrientation = executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
            var match = findInsertDatabaseSolution(
                    prefixedCube,
                    prefixedOrientation,
                    targetSlot,
                    protectedSlots,
                    logMatch
            );
            match.ifPresent(algorithm -> candidates.add(Algorithm.normalize(prefix.concat(algorithm))));
        }

        candidates.sort(RAW_ALGORITHM_COMPARATOR);
        return distinctAlgorithms(candidates);
    }

    private Optional<Algorithm> findSetupDatabaseSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        if (setupCaseDatabase.size() == 0) {
            return Optional.empty();
        }

        var insertSlot = visibleSlotForTarget(targetSlot, orientation);
        var preservedMask = preservationMaskFor(protectedSlots, orientation);
        var signature = F2LCaseSignatureExtractor.extract(cube, insertSlot, orientation);

        for (var setupCase : setupCaseDatabase.findCompatible(insertSlot, preservedMask, signature)) {
            SolveCancellation.throwIfCancelled();
            var trialCube = cube.copy();
            var resultingOrientation = executeAndReturnOrientation(trialCube, orientation, setupCase.algorithm().getMoves());
            if (areProtectedTargetsSolved(
                    trialCube,
                    targetCrossForOrientation(resultingOrientation),
                    protectedSlots
            )) {
                if (DEBUG_DB) {
                    System.out.println("[F2L SETUP DB] accepted insertSlot=" + insertSlot
                            + " preserved=" + preservedMask
                            + " case=" + setupCase.name()
                            + " algorithm=" + setupCase.algorithm());
                }
                return Optional.of(setupCase.algorithm());
            }

            if (DEBUG_VERBOSE) {
                System.out.println("[F2L SETUP DB] rejected insertSlot=" + insertSlot
                        + " preserved=" + preservedMask
                        + " case=" + setupCase.name()
                        + " algorithm=" + setupCase.algorithm());
            }
        }

        return Optional.empty();
    }

    private Optional<Algorithm> findInsertDatabaseSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        return findInsertDatabaseSolution(cube, orientation, targetSlot, protectedSlots, true);
    }

    private Optional<Algorithm> findInsertDatabaseSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            boolean logMatch
    ) {
        if (insertCaseDatabase.size() == 0) {
            return Optional.empty();
        }

        var insertSlot = visibleSlotForTarget(targetSlot, orientation);
        var preservedMask = preservationMaskFor(protectedSlots, orientation);
        var signature = F2LCaseSignatureExtractor.extract(cube, insertSlot, orientation);

        for (var insertCase : insertCaseDatabase.findCompatible(insertSlot, preservedMask, signature)) {
            SolveCancellation.throwIfCancelled();
            var trialCube = cube.copy();
            var resultingOrientation = executeAndReturnOrientation(trialCube, orientation, insertCase.algorithm().getMoves());
            if (areGoalsSolved(
                    trialCube,
                    targetCrossForOrientation(resultingOrientation),
                    targetSlot,
                    protectedSlots
            )) {
                if (logMatch && DEBUG_DB) {
                    System.out.println("[F2L INSERT DB] accepted insertSlot=" + insertSlot
                            + " preserved=" + preservedMask
                            + " case=" + insertCase.name()
                            + " algorithm=" + insertCase.algorithm());
                }
                return Optional.of(insertCase.algorithm());
            }

            if (logMatch && DEBUG_VERBOSE) {
                System.out.println("[F2L INSERT DB] rejected insertSlot=" + insertSlot
                        + " preserved=" + preservedMask
                        + " case=" + insertCase.name()
                        + " algorithm=" + insertCase.algorithm());
            }
        }

        return Optional.empty();
    }

    private F2LDatabaseMissContext databaseMissContext(
            F2LDatabaseMissContext.Phase phase,
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        var insertSlot = visibleSlotForTarget(targetSlot, orientation);
        return new F2LDatabaseMissContext(
                phase,
                insertSlot,
                preservationMaskFor(protectedSlots, orientation),
                F2LCaseSignatureExtractor.extract(cube, insertSlot, orientation),
                setupCaseDatabase.size(),
                insertCaseDatabase.size()
        );
    }

    private void handleDatabaseMiss(F2LDatabaseMissContext context) {
        if (DEBUG_DB) {
            System.out.println("[F2L DATABASE MISS] " + context);
        }
        if (context.phase() == F2LDatabaseMissContext.Phase.SETUP) {
            setupDatabaseMisses.increment();
        } else {
            insertDatabaseMisses.increment();
        }
        throw new F2LDatabaseMissException(context);
    }


    private static boolean areGoalsSolved(
            CubeState cube,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        if (!isTargetSlotSolved(cube, targetSlot)) {
            return false;
        }
        return areProtectedTargetsSolved(cube, targetCross, protectedSlots);
    }

    private static boolean areProtectedTargetsSolved(
            CubeState cube,
            Edge[] targetCross,
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

    private static boolean isSetupGoalSolved(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            SlotPair targetPair,
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
        return isPairConnected(cube, targetPair, orientation);
    }

    private static int setupHeuristic(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            SlotPair targetPair,
            List<TargetSlot> protectedSlots
    ) {
        if (isSetupGoalSolved(cube, orientation, targetCross, targetPair, protectedSlots)) {
            return 0;
        }

        int unsolvedParts = 0;
        for (var edge : targetCross) {
            if (cube.edgePerm[edge.ordinal()] != edge.ordinal() || cube.edgeOri[edge.ordinal()] != 0) {
                unsolvedParts++;
            }
        }
        for (var slot : protectedSlots) {
            if (!isTargetSlotSolved(cube, slot)) {
                unsolvedParts++;
            }
        }
        if (!isPairConnected(cube, targetPair, orientation)) {
            unsolvedParts++;
        }
        return Math.max(1, (unsolvedParts + 3) / 4);
    }

    private static int canonicalHeuristic(
            CubeState cube,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        int unsolvedParts = 0;
        for (var edge : targetCross) {
            if (cube.edgePerm[edge.ordinal()] != edge.ordinal() || cube.edgeOri[edge.ordinal()] != 0) {
                unsolvedParts++;
            }
        }
        for (var slot : relevantSlots(targetSlot, protectedSlots)) {
            if (cube.cornerPerm[slot.corner().ordinal()] != slot.corner().ordinal() || cube.cornerOri[slot.corner().ordinal()] != 0) {
                unsolvedParts++;
            }
            if (cube.edgePerm[slot.edge().ordinal()] != slot.edge().ordinal() || cube.edgeOri[slot.edge().ordinal()] != 0) {
                unsolvedParts++;
            }
        }
        return (unsolvedParts + 3) / 4;
    }

    private static long encodeState(CubeState cube, Edge[] targetCross, TargetSlot targetSlot, List<TargetSlot> protectedSlots) {
        long key = 0L;
        int shift = 0;

        for (var edge : targetCross) {
            key |= ((long) encodeEdge(cube, edge)) << shift;
            shift += 5;
        }

        for (var slot : relevantSlots(targetSlot, protectedSlots)) {
            key |= ((long) encodeCorner(cube, slot.corner())) << shift;
            shift += 5;
            key |= ((long) encodeEdge(cube, slot.edge())) << shift;
            shift += 5;
        }

        return key;
    }

    private static List<TargetSlot> relevantSlots(TargetSlot targetSlot, List<TargetSlot> protectedSlots) {
        var slots = new ArrayList<TargetSlot>(protectedSlots.size() + 1);
        slots.add(targetSlot);
        for (var slot : protectedSlots) {
            if (slot != targetSlot) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static F2LPreservationMask preservationMaskFor(List<TargetSlot> protectedSlots, CubeOrientation orientation) {
        var slots = new ArrayList<F2LSlot>();
        for (var protectedSlot : protectedSlots) {
            slots.add(visibleSlotForTarget(protectedSlot, orientation));
        }
        return F2LPreservationMask.of(slots);
    }

    private static int encodeCorner(CubeState cube, Corner targetCorner) {
        for (var position : Corner.values()) {
            if (cube.cornerPerm[position.ordinal()] == targetCorner.ordinal()) {
                return position.ordinal() | (cube.cornerOri[position.ordinal()] << 3);
            }
        }
        throw new IllegalStateException("Missing F2L corner: " + targetCorner);
    }

    private static int encodeEdge(CubeState cube, Edge targetEdge) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == targetEdge.ordinal()) {
                return position.ordinal() | (cube.edgeOri[position.ordinal()] << 4);
            }
        }
        throw new IllegalStateException("Missing F2L edge: " + targetEdge);
    }

    private static CubeOrientation executeAndReturnOrientation(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var orientedCube = new OrientedCube(cube, orientation);
        orientedCube.applyMoves(moves);
        return orientedCube.orientation();
    }

    private static List<Algorithm> distinctAlgorithms(List<Algorithm> algorithms) {
        var distinct = new LinkedHashMap<String, Algorithm>();
        for (var algorithm : algorithms) {
            distinct.putIfAbsent(algorithm.toString(), algorithm);
        }
        return List.copyOf(distinct.values());
    }

    private static List<PhaseSlotSolution> distinctPhaseSolutions(List<PhaseSlotSolution> solutions) {
        var distinct = new LinkedHashMap<String, PhaseSlotSolution>();
        for (var solution : solutions) {
            distinct.putIfAbsent(solution.targetSlot() + "|" + solution.algorithm(), solution);
        }
        return List.copyOf(distinct.values());
    }

    private static boolean areAllTargetsSolved(CubeState cube, TargetSlot[] targetSlots) {
        for (var targetSlot : targetSlots) {
            if (!isTargetSlotSolved(cube, targetSlot)) {
                return false;
            }
        }
        return true;
    }

    private final class OptimizedSearch {
        private final TargetSlot[] targetSlots;
        private final Consumer<F2LSearchProgress> progressListener;
        private final BooleanSupplier shouldStop;
        private final Map<String, List<Algorithm>> pathsByState = new HashMap<>();
        private final Map<String, List<F2LCandidate>> completedByState = new LinkedHashMap<>();
        private Algorithm bestAlgorithm;
        private long visitedStates;
        private long prunedStates;
        private long duplicateStates;
        private long lastProgressNanos;

        private OptimizedSearch(
                TargetSlot[] targetSlots,
                Consumer<F2LSearchProgress> progressListener,
                Algorithm initialBest,
                BooleanSupplier shouldStop
        ) {
            this.targetSlots = targetSlots;
            this.progressListener = progressListener;
            this.bestAlgorithm = Algorithm.normalize(initialBest);
            this.shouldStop = shouldStop;
        }

        private void search(OptimizedState state) {
            SolveCancellation.throwIfCancelled();
            if (shouldStop.getAsBoolean()) {
                publishProgress();
                return;
            }
            visitedStates++;
            publishProgress();

            if (areAllTargetsSolved(state.cube(), targetSlots)) {
                var completed = Algorithm.normalize(state.solution());
                registerCompletedCandidate(state, completed);
                if (ALGORITHM_COMPARATOR.compare(completed, bestAlgorithm) < 0) {
                    bestAlgorithm = completed;
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
                var nextSolution = Algorithm.normalize(state.solution().concat(candidate.algorithm()));
                search(new OptimizedState(
                        candidate.cube(),
                        candidate.orientation(),
                        candidate.protectedSlots(),
                        nextSolution
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

        private void registerCompletedCandidate(OptimizedState state, Algorithm algorithm) {
            var key = optimizedResultStateKey(state.cube(), state.orientation(), state.protectedSlots());
            var candidates = new ArrayList<>(completedByState.getOrDefault(key, List.of()));
            if (candidates.stream().anyMatch(existing -> existing.algorithm().toString().equals(algorithm.toString()))) {
                duplicateStates++;
                return;
            }
            candidates.add(new F2LCandidate(
                    algorithm,
                    state.cube().copy(),
                    state.orientation().copy()
            ));
            candidates.sort(Comparator.comparing(F2LCandidate::algorithm, ALGORITHM_COMPARATOR));
            if (candidates.size() > MAX_PATHS_PER_STATE) {
                candidates.remove(candidates.size() - 1);
            }
            completedByState.put(key, List.copyOf(candidates));
        }

        private List<OptimizedTransition> optimizedCandidates(OptimizedState state) {
            var candidates = new ArrayList<PhaseSlotSolution>();
            candidates.addAll(findInsertDatabaseSlotSolutions(
                    state.cube(),
                    state.orientation(),
                    targetSlots,
                    state.protectedSlots(),
                    this::checkpoint
            ));
            if (shouldStop.getAsBoolean()) {
                return List.of();
            }
            candidates.addAll(findSetupThenInsertDatabaseSlotSolutions(
                    state.cube(),
                    state.orientation(),
                    targetSlots,
                    state.protectedSlots(),
                    this::checkpoint
            ));
            candidates.sort(PHASE_SOLUTION_COMPARATOR);
            return slotCompletingTransitions(state, distinctPhaseSolutions(candidates));
        }

        private List<OptimizedTransition> slotCompletingTransitions(
                OptimizedState state,
                List<PhaseSlotSolution> candidates
        ) {
            var completingByState = new LinkedHashMap<String, OptimizedTransition>();
            for (var candidate : candidates) {
                if (shouldStop.getAsBoolean()) {
                    break;
                }
                var trialCube = state.cube().copy();
                var trialOrientation = executeAndReturnOrientation(
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
                    var transition = new OptimizedTransition(
                            candidate.algorithm(),
                            trialCube,
                            trialOrientation,
                            nextProtectedSlots
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
            completing.sort(Comparator.comparing(OptimizedTransition::algorithm, ALGORITHM_COMPARATOR));
            return List.copyOf(completing);
        }

        private String optimizedStateKey(OptimizedState state) {
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
            progressListener.accept(new F2LSearchProgress(
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

        private List<F2LCandidate> completedCandidates() {
            return completedByState.values().stream()
                    .map(candidates -> candidates.get(0))
                    .sorted(Comparator.comparing(F2LCandidate::algorithm, ALGORITHM_COMPARATOR))
                    .toList();
        }

        private long visitedStates() {
            return visitedStates;
        }

        private long prunedStates() {
            return prunedStates;
        }

        private long duplicateStates() {
            return duplicateStates;
        }
    }

    private record PhaseSlotSolution(TargetSlot targetSlot, Algorithm algorithm) {
    }

    private record OptimizedState(
            CubeState cube,
            CubeOrientation orientation,
            List<TargetSlot> protectedSlots,
            Algorithm solution
    ) {
    }

    private record OptimizedTransition(
            Algorithm algorithm,
            CubeState cube,
            CubeOrientation orientation,
            List<TargetSlot> protectedSlots
    ) {
    }

    @FunctionalInterface
    private interface SearchCheckpoint {
        SearchCheckpoint NEVER = () -> false;

        boolean shouldStop();
    }

    public enum SolvePhase {
        QUEUED,
        CROSS_EVALUATION,
        BASELINE_COMPARISON,
        BASELINE_BUDGET_REACHED,
        BASELINE_FALLBACK,
        F2L_CANDIDATE_GENERATION,
        F2L_OPTIMIZATION,
        OPTIMIZATION_BUDGET_REACHED,
        COMPLETE
    }

    public record F2LSearchProgress(
            long statesExplored,
            long statesPruned,
            long duplicateStates,
            int bestMoves,
            int completedCandidates,
            int candidatesEvaluated,
            int bestTotalMoves,
            SolvePhase phase,
            String currentCrossFace,
            int completedCrosses,
            int totalCrosses,
            int optimizationCandidate,
            int totalOptimizationCandidates,
            boolean optimizationBudgetExpired
    ) {
        public F2LSearchProgress(
                long statesExplored,
                long statesPruned,
                long duplicateStates,
                int bestMoves,
                int completedCandidates,
                int candidatesEvaluated,
                int bestTotalMoves
        ) {
            this(statesExplored, statesPruned, duplicateStates, bestMoves, completedCandidates,
                    candidatesEvaluated, bestTotalMoves, SolvePhase.F2L_CANDIDATE_GENERATION,
                    "", 0, 0, 0, 0, false);
        }

        public F2LSearchProgress withMetadata(
                SolvePhase phase,
                String currentCrossFace,
                int completedCrosses,
                int totalCrosses,
                int optimizationCandidate,
                int totalOptimizationCandidates,
                boolean optimizationBudgetExpired
        ) {
            return new F2LSearchProgress(
                    statesExplored, statesPruned, duplicateStates, bestMoves, completedCandidates,
                    candidatesEvaluated, bestTotalMoves, phase, currentCrossFace, completedCrosses,
                    totalCrosses, optimizationCandidate, totalOptimizationCandidates, optimizationBudgetExpired
            );
        }
    }

    public record F2LCandidate(
            Algorithm algorithm,
            CubeState cube,
            CubeOrientation orientation
    ) {
    }

    public record F2LDiagnostics(
            long setupDatabaseMisses,
            long insertDatabaseMisses
    ) {
        public long totalDatabaseMisses() {
            return setupDatabaseMisses + insertDatabaseMisses;
        }
    }

}
