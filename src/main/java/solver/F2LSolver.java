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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.LongConsumer;
import java.util.function.Consumer;

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
    private static final int MAX_SLOT_SEARCH_DEPTH = 12;
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

    private static final Move[] FACE_TURNS = {
            Move.U, Move.U2, Move.U_PRIME,
            Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME,
            Move.D, Move.D2, Move.D_PRIME,
            Move.L, Move.L2, Move.L_PRIME
    };
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

    public F2LSolver() {
        this(F2LSetupCaseDatabase.seedCases(), F2LInsertCaseDatabase.seedCases());
    }

    public F2LSolver(F2LSetupCaseDatabase setupCaseDatabase, F2LInsertCaseDatabase insertCaseDatabase) {
        this.setupCaseDatabase = setupCaseDatabase == null ? F2LSetupCaseDatabase.empty() : setupCaseDatabase;
        this.insertCaseDatabase = insertCaseDatabase == null ? F2LInsertCaseDatabase.empty() : insertCaseDatabase;
        this.setupCaseDatabase.validate();
        this.insertCaseDatabase.validate();
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
        var candidates = solveOptimizedCandidates(cube, progressListener);
        if (candidates.isEmpty()) {
            return solve(cube);
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
                        List.of(),
                        mapFaceTurns(orientation)
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
                if (!protectedSlots.contains(candidate.targetSlot())) {
                    protectedSlots.add(candidate.targetSlot());
                }
                continue;
            }

            var setupSolution = findBestSetupDatabaseSlotSolution(workingCube, currentOrientation, targetSlots, protectedSlots);
            if (setupSolution.isPresent()) {
                var candidate = setupSolution.get();
                if (DEBUG_DB) {
                    System.out.println("[F2L STAGE] setup DB selected targetSlot=" + candidate.targetSlot()
                            + " algorithm=" + candidate.algorithm());
                }
                currentOrientation = executeAndReturnOrientation(workingCube, currentOrientation, candidate.algorithm().getMoves());
                solution = solution.concat(candidate.algorithm());
                if (isTargetSlotSolved(workingCube, candidate.targetSlot()) && !protectedSlots.contains(candidate.targetSlot())) {
                    protectedSlots.add(candidate.targetSlot());
                }
                continue;
            }

            var targetCross = targetCrossForOrientation(currentOrientation);
            var faceTurns = mapFaceTurns(currentOrientation);
            var targetSlot = fallbackTarget.get();
            var slotSolution = solveSlotInternal(workingCube, currentOrientation, targetCross, targetSlot, protectedSlots, faceTurns);
            currentOrientation = executeAndReturnOrientation(workingCube, currentOrientation, slotSolution.getMoves());
            solution = solution.concat(slotSolution);
            if (!protectedSlots.contains(targetSlot)) {
                protectedSlots.add(targetSlot);
            }
        }

        return Algorithm.normalize(solution);
    }

    public List<F2LCandidate> solveOptimizedCandidates(
            OrientedCube cube,
            Consumer<F2LSearchProgress> progressListener
    ) {
        var initialCube = cube.cubeState().copy();
        var initialOrientation = cube.orientation();
        var targetSlots = targetSlotsForOrientation(initialOrientation);
        var protectedSlots = initiallyProtectedSlots(initialCube, targetSlots);
        ensureCrossSolved(initialCube, targetCrossForOrientation(initialOrientation));

        var greedyUpperBound = solveForTargets(initialCube.copy(), initialOrientation.copy());
        var search = new OptimizedSearch(
                targetSlots,
                progressListener == null ? ignored -> {
                } : progressListener,
                greedyUpperBound
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
            List<TargetSlot> protectedSlots
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var targetSlot : targetSlots) {
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            for (var algorithm : findPrefixedInsertDatabaseSolutions(cube, orientation, targetSlot, protectedSlots, true)) {
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
            List<TargetSlot> protectedSlots
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var targetSlot : targetSlots) {
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            for (var algorithm : findPrefixedSetupThenInsertDatabaseSolutions(cube, orientation, targetSlot, protectedSlots)) {
                candidates.add(new PhaseSlotSolution(targetSlot, algorithm));
            }
        }
        candidates.sort(RAW_PHASE_SOLUTION_COMPARATOR);
        return distinctPhaseSolutions(candidates);
    }

    private Optional<PhaseSlotSolution> findBestSetupDatabaseSlotSolution(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var targetSlot : targetSlots) {
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            var solution = findPrefixedSetupDatabaseSolution(cube, orientation, targetSlot, protectedSlots);
            solution.map(algorithm -> new PhaseSlotSolution(targetSlot, algorithm))
                    .ifPresent(candidates::add);
        }
        candidates.sort(Comparator.comparingInt(candidate -> candidate.algorithm().getMoves().size()));
        return candidates.stream().findFirst();
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
            var setupCube = cube.copy();
            var setupOrientation = executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
            findPrefixedInsertDatabaseSolution(setupCube, setupOrientation, targetSlot, protectedSlots, false)
                    .ifPresent(insert -> candidates.add(Algorithm.normalize(setup.concat(insert))));
        }

        for (var setup : findPrefixedValidatedSetupAlgorithms(cube, orientation, targetSlot, protectedSlots)) {
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
            List<TargetSlot> protectedSlots
    ) {
        if (setupCaseDatabase.size() == 0 || insertCaseDatabase.size() == 0) {
            return List.of();
        }

        var candidates = new ArrayList<Algorithm>();
        for (var setup : findPrefixedSetupDatabaseSolutions(cube, orientation, targetSlot, protectedSlots)) {
            var setupCube = cube.copy();
            var setupOrientation = executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
            for (var insert : findPrefixedInsertDatabaseSolutions(setupCube, setupOrientation, targetSlot, protectedSlots, false)) {
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
            List<TargetSlot> protectedSlots,
            Move[] faceTurns
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

        var setup = solveSetupPhase(cube, orientation, targetCross, targetPair, targetSlot, protectedSlots, faceTurns);
        var setupCube = cube.copy();
        var setupOrientation = executeAndReturnOrientation(setupCube, orientation, setup.getMoves());

        var insert = solveInsertPhase(
                setupCube,
                setupOrientation,
                targetCrossForOrientation(setupOrientation),
                targetSlot,
                protectedSlots,
                mapFaceTurns(setupOrientation)
        );
        return Algorithm.normalize(setup.concat(insert));
    }

    private Algorithm solveSetupPhase(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            SlotPair targetPair,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            Move[] faceTurns
    ) {
        var databaseSolution = findPrefixedSetupDatabaseSolution(cube, orientation, targetSlot, protectedSlots);
        if (databaseSolution.isPresent()) {
            return databaseSolution.get();
        }

        return solveSearchPhase(
                cube,
                faceTurns,
                "F2L setup",
                new SearchGoal(
                        state -> isSetupGoalSolved(state, orientation, targetCross, targetPair, protectedSlots),
                        state -> setupHeuristic(state, orientation, targetCross, targetPair, protectedSlots),
                        state -> encodeState(state, targetCross, targetSlot, protectedSlots)
                )
        );
    }

    private Algorithm solveInsertPhase(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            Move[] faceTurns
    ) {
        var databaseSolution = findPrefixedInsertDatabaseSolution(cube, orientation, targetSlot, protectedSlots);
        if (databaseSolution.isPresent()) {
            return databaseSolution.get();
        }

        return solveSearchPhase(
                cube,
                faceTurns,
                "F2L insert",
                new SearchGoal(
                        state -> areGoalsSolved(state, targetCross, targetSlot, protectedSlots),
                        state -> canonicalHeuristic(state, targetCross, targetSlot, protectedSlots),
                        state -> encodeState(state, targetCross, targetSlot, protectedSlots)
                )
        );
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
        var candidates = new ArrayList<Algorithm>();
        if (setupCaseDatabase.size() == 0) {
            return candidates;
        }

        for (var prefix : DB_PREFIX_TRIALS) {
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
            var prefixedCube = cube.copy();
            var prefixedOrientation = executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
            var preservedMask = preservationMaskFor(protectedSlots, prefixedOrientation);

            for (var setupCase : setupCaseDatabase.allCases()) {
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
                    if (DEBUG_VERBOSE) {
                        System.out.println("[F2L SETUP DB] validation candidate targetSlot=" + targetSlot
                                + " prefix=" + printableAlgorithm(prefix)
                                + " case=" + setupCase.name()
                                + " algorithm=" + setup);
                    }
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
        if (insertCaseDatabase.size() == 0) {
            return List.of();
        }

        var candidates = new ArrayList<Algorithm>();
        for (var prefix : DB_PREFIX_TRIALS) {
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

    private Algorithm solveSearchPhase(
            CubeState cube,
            Move[] faceTurns,
            String phaseName,
            SearchGoal goal
    ) {
        var path = new ArrayList<Move>();
        int bound = goal.heuristic(cube);
        while (bound <= MAX_SLOT_SEARCH_DEPTH) {
            SolveCancellation.throwIfCancelled();
            var outcome = idaSearch(
                    cube.copy(),
                    faceTurns,
                    goal,
                    path,
                    null,
                    0,
                    bound,
                    new HashMap<>()
            );
            if (outcome.solution() != null) {
                return outcome.solution();
            }
            if (outcome.nextBound() == Integer.MAX_VALUE) {
                break;
            }
            bound = outcome.nextBound();
        }

        throw new IllegalStateException("Failed to find an " + phaseName + " solution");
    }


    private SearchOutcome idaSearch(
            CubeState cube,
            Move[] faceTurns,
            SearchGoal goal,
            List<Move> path,
            Move lastMove,
            int depth,
            int bound,
            Map<Long, Integer> visitedDepth
    ) {
        SolveCancellation.throwIfCancelled();
        int estimate = depth + goal.heuristic(cube);
        if (estimate > bound) {
            return new SearchOutcome(null, estimate);
        }
        if (goal.isSolved(cube)) {
            return new SearchOutcome(Algorithm.fromMoves(List.copyOf(path)), depth);
        }

        long stateKey = goal.stateKey(cube);
        var bestDepth = visitedDepth.get(stateKey);
        if (bestDepth != null && bestDepth <= depth) {
            return new SearchOutcome(null, Integer.MAX_VALUE);
        }
        visitedDepth.put(stateKey, depth);

        int nextBound = Integer.MAX_VALUE;

        for (int i = 0; i < FACE_TURNS.length; i++) {
            var move = FACE_TURNS[i];
            if (lastMove != null && sameFace(lastMove, move)) {
                continue;
            }

            var nextCube = cube.copy();
            MoveApplier.applyMove(nextCube, faceTurns[i]);
            path.add(move);

            var outcome = idaSearch(
                    nextCube,
                    faceTurns,
                    goal,
                    path,
                    move,
                    depth + 1,
                    bound,
                    visitedDepth
            );
            if (outcome.solution() != null) {
                return outcome;
            }
            nextBound = Math.min(nextBound, outcome.nextBound());

            path.remove(path.size() - 1);
        }

        return new SearchOutcome(null, nextBound);
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

    private static boolean sameFace(Move first, Move second) {
        return faceFamily(first) == faceFamily(second);
    }

    private static Face faceFamily(Move move) {
        return switch (move) {
            case U, U2, U_PRIME -> Face.U;
            case R, R2, R_PRIME -> Face.R;
            case F, F2, F_PRIME -> Face.F;
            case D, D2, D_PRIME -> Face.D;
            case L, L2, L_PRIME -> Face.L;
            default -> throw new IllegalArgumentException("Unexpected F2L move: " + move);
        };
    }

    private static Move[] mapFaceTurns(CubeOrientation orientation) {
        var mapped = new Move[FACE_TURNS.length];
        for (int i = 0; i < FACE_TURNS.length; i++) {
            mapped[i] = orientation.mapMove(FACE_TURNS[i]);
        }
        return mapped;
    }

    private static CubeOrientation executeAndReturnOrientation(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var orientedCube = new OrientedCube(cube, orientation);
        orientedCube.applyMoves(moves);
        return orientedCube.orientation();
    }

    private static String printableAlgorithm(Algorithm algorithm) {
        return algorithm.isEmpty() ? "<none>" : algorithm.toString();
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
        private final Map<String, List<Algorithm>> pathsByState = new HashMap<>();
        private final Map<String, List<F2LCandidate>> completedByState = new LinkedHashMap<>();
        private Algorithm bestAlgorithm;
        private long visitedStates;
        private long prunedStates;
        private long duplicateStates;

        private OptimizedSearch(
                TargetSlot[] targetSlots,
                Consumer<F2LSearchProgress> progressListener,
                Algorithm initialBest
        ) {
            this.targetSlots = targetSlots;
            this.progressListener = progressListener;
            this.bestAlgorithm = Algorithm.normalize(initialBest);
        }

        private void search(OptimizedState state) {
            SolveCancellation.throwIfCancelled();
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
                    state.protectedSlots()
            ));
            candidates.addAll(findSetupThenInsertDatabaseSlotSolutions(
                    state.cube(),
                    state.orientation(),
                    targetSlots,
                    state.protectedSlots()
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

    public record F2LSearchProgress(
            long statesExplored,
            long statesPruned,
            long duplicateStates,
            int bestMoves,
            int completedCandidates,
            int candidatesEvaluated,
            int bestTotalMoves
    ) {
    }

    public record F2LCandidate(
            Algorithm algorithm,
            CubeState cube,
            CubeOrientation orientation
    ) {
    }

    private record SearchOutcome(Algorithm solution, int nextBound) {
    }

    private record SearchGoal(
            Predicate<CubeState> isSolved,
            ToIntFunction<CubeState> heuristic,
            ToLongFunction<CubeState> stateKey
    ) {
        boolean isSolved(CubeState cube) {
            return isSolved.test(cube);
        }

        int heuristic(CubeState cube) {
            return heuristic.applyAsInt(cube);
        }

        long stateKey(CubeState cube) {
            return stateKey.applyAsLong(cube);
        }
    }
}
