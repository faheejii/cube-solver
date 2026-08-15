package solver;

import algorithms.F2LInsertCaseDatabase;
import algorithms.F2LSetupCaseDatabase;
import cfop.F2LAnalyzer;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry.SlotPair;
import cfop.F2LGeometry.TargetSlot;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cfop.F2LSetupSignature;
import cube.Algorithm;
import cube.Corner;
import cube.CubeOrientation;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.CubeStateSnapshot;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.OrientationFrames;
import cube.OrientedCube;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final boolean DEBUG_DB = Boolean.getBoolean("f2l.debug");
    private static final boolean DEBUG_VERBOSE = Boolean.getBoolean("f2l.debug.verbose");
    private static final Comparator<Algorithm> ALGORITHM_COMPARATOR = Comparator
            .comparingInt(Algorithm::getMoveCount)
            .thenComparingInt(algorithm -> algorithm.getMoves().size())
            .thenComparing(Algorithm::toString);
    private static final Comparator<Algorithm> RAW_ALGORITHM_COMPARATOR = Comparator
            .comparingInt((Algorithm algorithm) -> algorithm.getMoves().size());
    private static final Comparator<PhaseSlotSolution> PHASE_SOLUTION_COMPARATOR = Comparator
            .comparing(PhaseSlotSolution::algorithm, ALGORITHM_COMPARATOR)
            .thenComparing(solution -> solution.targetSlot().toString());
    private static final Comparator<PhaseSlotSolution> RAW_PHASE_SOLUTION_COMPARATOR = Comparator
            .comparing(PhaseSlotSolution::algorithm, RAW_ALGORITHM_COMPARATOR);

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
    private static final int MAX_SETUP_MATCHES_PER_VIEW = Integer.getInteger(
            "f2l.optimized.max-setup-matches", 64
    );
    private static final Map<F2LSlot, Algorithm> RECOVERY_UNPAIR_ALGORITHMS = Map.of(
            F2LSlot.FR, Algorithm.fromMoves(List.of(Move.R, Move.U, Move.R_PRIME)),
            F2LSlot.FL, Algorithm.fromMoves(List.of(Move.L_PRIME, Move.U_PRIME, Move.L)),
            F2LSlot.BR, Algorithm.fromMoves(List.of(Move.R_PRIME, Move.U_PRIME, Move.R)),
            F2LSlot.BL, Algorithm.fromMoves(List.of(Move.L, Move.U, Move.L_PRIME))
    );
    private static final int MAX_RECOVERY_MOVES = 12;
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
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        return Algorithm.materializeCubeRotations(solveStage(cube.copy(), orientation), orientation);
    }

    public Algorithm solve(OrientedCube cube) {
        return solveTrace(cube).algorithm();
    }

    public F2LSolveTrace solveTrace(OrientedCube cube) {
        if (cube == null) {
            throw new IllegalArgumentException("cube cannot be null");
        }
        return solveForTargetsTrace(cube.cubeState().copy(), cube.orientation());
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
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        return Algorithm.materializeCubeRotations(solveStage(cube.copy(), orientation), orientation);
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot) {
        return solveSlot(cube, slot, Face.D);
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot, Face crossFace) {
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        return Algorithm.materializeCubeRotations(
                solveSlotInternal(
                        cube.copy(),
                        orientation,
                        targetCrossForOrientation(orientation),
                        targetSlotFor(slot, orientation),
                        List.of()
                ),
                orientation
        );
    }

    private Algorithm solveForTargets(CubeState cube, CubeOrientation orientation) {
        return solveForTargetsTrace(cube, orientation).algorithm();
    }

    private F2LSolveTrace solveForTargetsTrace(CubeState cube, CubeOrientation orientation) {
        var targetSlots = targetSlotsForOrientation(orientation);
        var workingCube = cube.copy();
        var currentOrientation = orientation.copy();
        ensureCrossSolved(workingCube, targetCrossForOrientation(currentOrientation));

        var pendingSteps = new ArrayList<PendingPairStep>();
        var protectedSlots = new ArrayList<TargetSlot>();
        var stepStartCube = workingCube.copy();
        var stepStartOrientation = currentOrientation.copy();
        var recoveryMoves = new ArrayList<Move>();
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
                var pendingStep = pendingPairStep(
                        stepStartCube, stepStartOrientation,
                        workingCube, currentOrientation,
                        candidate.targetSlot(), candidate.algorithm(), protectedSlots, true,
                        recoveryMoves
                );
                if (DEBUG_DB) {
                    System.out.println("[F2L STAGE] insert DB selected targetSlot=" + candidate.targetSlot()
                            + " algorithm=" + candidate.algorithm());
                }
                currentOrientation = F2LStateCodec.executeAndReturnOrientation(workingCube, currentOrientation, candidate.algorithm().getMoves());
                ensureTargetSlotSolved(workingCube, candidate.targetSlot(), "insert database");
                pendingSteps.add(pendingStep.withAfter(workingCube, currentOrientation));
                if (!protectedSlots.contains(candidate.targetSlot())) {
                    protectedSlots.add(candidate.targetSlot());
                }
                stepStartCube = workingCube.copy();
                stepStartOrientation = currentOrientation.copy();
                recoveryMoves.clear();
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
                var pendingStep = pendingPairStep(
                        stepStartCube, stepStartOrientation,
                        workingCube, currentOrientation,
                        candidate.targetSlot(), candidate.algorithm(), protectedSlots, true,
                        recoveryMoves
                );
                if (DEBUG_DB) {
                    System.out.println("[F2L STAGE] setup+insert DB selected targetSlot=" + candidate.targetSlot()
                            + " algorithm=" + candidate.algorithm());
                }
                currentOrientation = F2LStateCodec.executeAndReturnOrientation(workingCube, currentOrientation, candidate.algorithm().getMoves());
                ensureTargetSlotSolved(workingCube, candidate.targetSlot(), "setup+insert database");
                pendingSteps.add(pendingStep.withAfter(workingCube, currentOrientation));
                if (!protectedSlots.contains(candidate.targetSlot())) {
                    protectedSlots.add(candidate.targetSlot());
                }
                stepStartCube = workingCube.copy();
                stepStartOrientation = currentOrientation.copy();
                recoveryMoves.clear();
                continue;
            }

            var recovery = findRecoveryTransition(
                    workingCube, currentOrientation, targetSlots, protectedSlots
            );
            if (recovery.isPresent()) {
                var transition = recovery.get();
                currentOrientation = F2LStateCodec.executeAndReturnOrientation(
                        workingCube, currentOrientation, transition.algorithm().getMoves()
                );
                recoveryMoves.addAll(transition.algorithm().getMoves());
                if (recoveryMoves.size() > MAX_RECOVERY_MOVES) {
                    throw new F2LDatabaseMissException(databaseMissContext(
                            F2LDatabaseMissContext.Phase.SETUP,
                            workingCube, currentOrientation, fallbackTarget.get(), protectedSlots
                    ));
                }
                if (DEBUG_DB) {
                    System.out.println("[F2L RECOVERY] unpaired slot=" + transition.slot()
                            + " algorithm=" + transition.algorithm());
                }
                continue;
            }

            var targetCross = targetCrossForOrientation(currentOrientation);
            var targetSlot = fallbackTarget.get();
            var slotSolution = solveSlotInternal(workingCube, currentOrientation, targetCross, targetSlot, protectedSlots);
            var pendingStep = pendingPairStep(
                    stepStartCube, stepStartOrientation,
                    workingCube, currentOrientation,
                    targetSlot, slotSolution, protectedSlots, false, recoveryMoves
            );
            currentOrientation = F2LStateCodec.executeAndReturnOrientation(workingCube, currentOrientation, slotSolution.getMoves());
            ensureTargetSlotSolved(workingCube, targetSlot, "fallback search");
            pendingSteps.add(pendingStep.withAfter(workingCube, currentOrientation));
            if (!protectedSlots.contains(targetSlot)) {
                protectedSlots.add(targetSlot);
            }
            stepStartCube = workingCube.copy();
            stepStartOrientation = currentOrientation.copy();
            recoveryMoves.clear();
        }

        var steps = buildPairSteps(pendingSteps);
        var completeMoves = new ArrayList<Move>();
        for (var step : steps) {
            completeMoves.addAll(step.completeMoves());
        }
        return new F2LSolveTrace(
                completeMoves,
                steps,
                F2LAnalyzer.isF2LSolved(workingCube, currentOrientation)
        );
    }

    private static void ensureTargetSlotSolved(CubeState cube, TargetSlot targetSlot, String source) {
        if (!isTargetSlotSolved(cube, targetSlot)) {
            throw new IllegalStateException(source + " did not solve target slot " + targetSlot);
        }
    }

    private static PendingPairStep pendingPairStep(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            Algorithm algorithm,
            List<TargetSlot> protectedSlots,
            boolean shortestAvailable
    ) {
        return pendingPairStep(
                cube, orientation, cube, orientation, targetSlot, algorithm,
                protectedSlots, shortestAvailable, List.of()
        );
    }

    private static PendingPairStep pendingPairStep(
            CubeState stepStartCube,
            CubeOrientation stepStartOrientation,
            CubeState caseCube,
            CubeOrientation caseOrientation,
            TargetSlot targetSlot,
            Algorithm algorithm,
            List<TargetSlot> protectedSlots,
            boolean shortestAvailable,
            List<Move> recoveryMoves
    ) {
        return pendingPairStep(
                stepStartCube, stepStartOrientation, caseCube, caseOrientation,
                targetSlot, algorithm, protectedSlots, shortestAvailable,
                recoveryMoves, !recoveryMoves.isEmpty()
        );
    }

    private static PendingPairStep pendingPairStep(
            CubeState stepStartCube,
            CubeOrientation stepStartOrientation,
            CubeState caseCube,
            CubeOrientation caseOrientation,
            TargetSlot targetSlot,
            Algorithm algorithm,
            List<TargetSlot> protectedSlots,
            boolean shortestAvailable,
            List<Move> recoveryMoves,
            boolean recoveryUsed
    ) {
        var visibleSlot = visibleSlotForTarget(targetSlot, caseOrientation);
        var signature = F2LCaseSignatureExtractor.extract(caseCube, visibleSlot, caseOrientation);
        var pair = new SlotPair(targetSlot.corner(), targetSlot.edge());
        var caseDescription = new F2LCaseDescription(
                signature,
                isPairConnected(caseCube, pair, caseOrientation),
                isCornerInTargetSlot(caseCube, targetSlot),
                isEdgeInMiddleLayer(caseCube, targetSlot.edge())
        );
        var preservedSlots = F2LPreservationMask.of(
                protectedSlots.stream()
                        .map(protectedSlot -> visibleSlotForTarget(protectedSlot, caseOrientation))
                        .toList()
        );
        var completeMoves = new ArrayList<Move>(recoveryMoves.size() + algorithm.getMoves().size());
        completeMoves.addAll(recoveryMoves);
        completeMoves.addAll(algorithm.getMoves());
        return new PendingPairStep(
                targetSlot.corner(),
                targetSlot.edge(),
                visibleSlot,
                List.copyOf(completeMoves),
                CubeStateSnapshot.from(stepStartCube),
                CubeOrientationKey.from(stepStartOrientation),
                preservedSlots,
                caseDescription,
                shortestAvailable,
                recoveryUsed
        );
    }

    static List<F2LPairStep> buildPairSteps(List<PendingPairStep> pendingSteps) {
        var totalMoves = pendingSteps.stream()
                .mapToInt(step -> Algorithm.fromMoves(step.completeMoves()).getMoveCount())
                .sum();
        var steps = new ArrayList<F2LPairStep>(pendingSteps.size());
        for (int index = 0; index < pendingSteps.size(); index++) {
            var pending = pendingSteps.get(index);
            var algorithm = Algorithm.fromMoves(pending.completeMoves());
            var remainingMoves = pendingSteps.subList(index + 1, pendingSteps.size()).stream()
                    .mapToInt(step -> Algorithm.fromMoves(step.completeMoves()).getMoveCount())
                    .sum();
            var rotationCount = (int) pending.completeMoves().stream()
                    .filter(Move::isCubeRotation)
                    .count();
            var reasonCodes = new ArrayList<F2LReasonCode>();
            if (pending.caseDescription().initiallyConnected()) {
                reasonCodes.add(F2LReasonCode.PAIR_ALREADY_CONNECTED);
            }
            if (pending.recoveryUsed()) {
                reasonCodes.add(F2LReasonCode.RECOVERY_UNPAIR);
            }
            if (!pending.preservedSlots().slots().isEmpty()) {
                reasonCodes.add(F2LReasonCode.PRESERVES_SOLVED_SLOTS);
            }
            if (pending.shortestAvailable()) {
                reasonCodes.add(F2LReasonCode.SHORTEST_AVAILABLE_PAIR);
            }
            if (rotationCount == 0) {
                reasonCodes.add(F2LReasonCode.NO_ROTATION_REQUIRED);
            }
            var evidence = new F2LSelectionEvidence(
                    algorithm.getMoveCount(),
                    remainingMoves,
                    totalMoves,
                    rotationCount,
                    !pending.preservedSlots().slots().isEmpty(),
                    pending.caseDescription().initiallyConnected(),
                    pending.shortestAvailable(),
                    false,
                    reasonCodes
            );
            steps.add(new F2LPairStep(
                    index + 1,
                    pending.corner(),
                    pending.edge(),
                    pending.targetSlot(),
                    List.of(),
                    List.of(),
                    List.of(),
                    pending.completeMoves(),
                    false,
                    pending.stateBefore(),
                    pending.orientationBefore(),
                    pending.stateAfter(),
                    pending.orientationAfter(),
                    pending.preservedSlots(),
                    pending.caseDescription(),
                    evidence
            ));
        }
        return List.copyOf(steps);
    }

    private static boolean isCornerInTargetSlot(CubeState cube, TargetSlot targetSlot) {
        var position = targetSlot.corner().ordinal();
        return cube.cornerPerm[position] == position && cube.cornerOri[position] == 0;
    }

    private static boolean isEdgeInMiddleLayer(CubeState cube, Edge targetEdge) {
        for (var position : List.of(Edge.FR, Edge.FL, Edge.BL, Edge.BR)) {
            if (cube.edgePerm[position.ordinal()] == targetEdge.ordinal()) {
                return true;
            }
        }
        return false;
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
        var search = new F2LOptimizedSearch(
                targetSlots,
                progressListener == null ? ignored -> {
                } : progressListener,
                effectiveGreedyUpperBound,
                shouldStop == null ? () -> false : shouldStop,
                new F2LOptimizedSearch.Host() {
                    @Override
                    public List<PhaseSlotSolution> findInsertDatabaseSlotSolutions(
                            CubeState cube,
                            CubeOrientation orientation,
                            TargetSlot[] targetSlots,
                            List<TargetSlot> protectedSlots,
                            SearchCheckpoint checkpoint
                    ) {
                        return F2LSolver.this.findInsertDatabaseSlotSolutions(
                                cube, orientation, targetSlots, protectedSlots, checkpoint
                        );
                    }

                    @Override
                    public List<PhaseSlotSolution> findSetupThenInsertDatabaseSlotSolutions(
                            CubeState cube,
                            CubeOrientation orientation,
                            TargetSlot[] targetSlots,
                            List<TargetSlot> protectedSlots,
                            SearchCheckpoint checkpoint
                    ) {
                        return F2LSolver.this.findSetupThenInsertDatabaseSlotSolutions(
                                cube, orientation, targetSlots, protectedSlots, checkpoint
                        );
                    }

                    @Override
                    public List<PhaseSlotSolution> findRecoveryThenDatabaseSolutions(
                            OptimizedState state,
                            SearchCheckpoint checkpoint
                    ) {
                        return F2LSolver.this.findRecoveryThenDatabaseSolutions(state, checkpoint);
                    }

                    @Override
                    public PendingPairStep pendingPairStep(
                            OptimizedState state,
                            PhaseSlotSolution candidate,
                            CubeState trialCube,
                            CubeOrientation trialOrientation
                    ) {
                        var pendingStep = F2LSolver.pendingPairStep(
                                state.cube(),
                                state.orientation(),
                                candidate.caseCube() == null ? state.cube() : candidate.caseCube(),
                                candidate.caseOrientation() == null
                                        ? state.orientation()
                                        : candidate.caseOrientation(),
                                candidate.targetSlot(),
                                candidate.algorithm(),
                                state.protectedSlots(),
                                false,
                                List.of(),
                                candidate.recoveryUsed()
                        );
                        return pendingStep.withAfter(trialCube, trialOrientation);
                    }

                    @Override
                    public List<F2LPairStep> buildPairSteps(List<PendingPairStep> pendingSteps) {
                        return F2LSolver.buildPairSteps(pendingSteps);
                    }
                }
        );
        search.search(new OptimizedState(
                initialCube,
                initialOrientation.copy(),
                protectedSlots,
                new Algorithm(),
                List.of()
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
        var updated = new ArrayList<>(current);
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
            solution.map(algorithm -> new PhaseSlotSolution(targetSlot, algorithm, false, null, null))
                    .ifPresent(candidates::add);
        }
        candidates.sort(Comparator.comparingInt(candidate -> candidate.algorithm().getMoves().size()));
        return candidates.stream().findFirst();
    }

    List<PhaseSlotSolution> findInsertDatabaseSlotSolutions(
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
                candidates.add(new PhaseSlotSolution(targetSlot, algorithm, false, null, null));
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
        return findSetupThenInsertDatabaseSlotSolutions(
                cube, orientation, targetSlots, protectedSlots, SearchCheckpoint.NEVER
        ).stream().findFirst();
    }

    List<PhaseSlotSolution> findSetupThenInsertDatabaseSlotSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots,
            SearchCheckpoint checkpoint
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        for (var setup : findPrefixedSetupDatabaseSolutions(
                cube, orientation, targetSlots, protectedSlots, checkpoint
        )) {
            if (checkpoint.shouldStop()) {
                break;
            }
            var setupCube = cube.copy();
            var setupOrientation = F2LStateCodec.executeAndReturnOrientation(
                    setupCube, orientation, setup.getMoves()
            );
            if (!areProtectedTargetsSolved(
                    setupCube, targetCrossForOrientation(setupOrientation), protectedSlots
            )) {
                continue;
            }

            // A setup is slot-independent. It may prepare any unresolved pair,
            // so the insertion target is selected only after the setup has
            // been applied. A setup-only completion is valid only when it
            // completes exactly one new pair.
            var newlySolved = newlySolvedSlots(setupCube, targetSlots, protectedSlots, cube);
            if (newlySolved.size() == 1) {
                candidates.add(new PhaseSlotSolution(
                        newlySolved.get(0), setup, false, null, null
                ));
            }
            if (!newlySolved.isEmpty()) {
                continue;
            }

            for (var insert : findInsertDatabaseSlotSolutions(
                    setupCube, setupOrientation, targetSlots, protectedSlots, checkpoint
            )) {
                var route = Algorithm.normalize(setup.concat(insert.algorithm()));
                candidates.add(new PhaseSlotSolution(insert.targetSlot(), route, false, null, null));
            }
        }
        candidates.sort(RAW_PHASE_SOLUTION_COMPARATOR);
        return distinctPhaseSolutions(candidates);
    }

    private static List<TargetSlot> newlySolvedSlots(
            CubeState after,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots,
            CubeState before
    ) {
        var newlySolved = new ArrayList<TargetSlot>();
        for (var targetSlot : targetSlots) {
            if (!protectedSlots.contains(targetSlot)
                    && !isTargetSlotSolved(before, targetSlot)
                    && isTargetSlotSolved(after, targetSlot)) {
                newlySolved.add(targetSlot);
            }
        }
        return List.copyOf(newlySolved);
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
            var setupOrientation = F2LStateCodec.executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
            findPrefixedInsertDatabaseSolution(setupCube, setupOrientation, targetSlot, protectedSlots, false)
                    .ifPresent(insert -> candidates.add(Algorithm.normalize(setup.concat(insert))));
        }

        for (var setup : findPrefixedValidatedSetupAlgorithms(cube, orientation, targetSlot, protectedSlots)) {
            SolveCancellation.throwIfCancelled();
            var setupCube = cube.copy();
            var setupOrientation = F2LStateCodec.executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
            findPrefixedInsertDatabaseSolution(setupCube, setupOrientation, targetSlot, protectedSlots, false)
                    .ifPresent(insert -> {
                        if (DEBUG_DB) {
                            System.out.println("[F2L SELECTED VALIDATED SETUP+INSERT] setup=" + setup + " insert=" + insert);
                        }
                        candidates.add(Algorithm.normalize(setup.concat(insert)));
                    });
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
            var setupOrientation = F2LStateCodec.executeAndReturnOrientation(setupCube, orientation, setup.getMoves());
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
            var setupOrientation = F2LStateCodec.executeAndReturnOrientation(setupCube, orientation, setup.getMoves());

        var insert = solveInsertPhase(
                setupCube,
                setupOrientation,
                targetCrossForOrientation(setupOrientation),
                targetSlot,
                protectedSlots
        );
        return Algorithm.normalize(setup.concat(insert));
    }

    private Optional<RecoveryTransition> findRecoveryTransition(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots
    ) {
        var candidates = new ArrayList<RecoveryTransition>();
        for (var slot : F2LSlot.values()) {
            SolveCancellation.throwIfCancelled();
            var targetSlot = targetSlotFor(slot, orientation);
            if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(cube, targetSlot)) {
                continue;
            }
            var recovery = RECOVERY_UNPAIR_ALGORITHMS.get(slot);
            var trialCube = cube.copy();
            var trialOrientation = F2LStateCodec.executeAndReturnOrientation(trialCube, orientation, recovery.getMoves());
            if (!areProtectedTargetsSolved(
                    trialCube, targetCrossForOrientation(trialOrientation), protectedSlots
            )) {
                continue;
            }
            if (!hasDatabaseRoute(trialCube, trialOrientation, targetSlots, protectedSlots)) {
                continue;
            }
            candidates.add(new RecoveryTransition(slot, recovery));
        }
        return candidates.stream().min(Comparator.comparingInt((RecoveryTransition candidate) -> candidate.algorithm().getMoves().size())
                .thenComparing(candidate -> candidate.slot().name()));
    }

    private boolean hasDatabaseRoute(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots
    ) {
        return findBestInsertDatabaseSlotSolution(cube, orientation, targetSlots, protectedSlots).isPresent()
                || findBestSetupThenInsertDatabaseSlotSolution(cube, orientation, targetSlots, protectedSlots).isPresent();
    }

    List<PhaseSlotSolution> findRecoveryThenDatabaseSolutions(
            OptimizedState state,
            SearchCheckpoint checkpoint
    ) {
        var candidates = new ArrayList<PhaseSlotSolution>();
        var targetSlots = targetSlotsForOrientation(state.orientation());
        for (var slot : F2LSlot.values()) {
            if (checkpoint.shouldStop()) {
                break;
            }
            var recovery = RECOVERY_UNPAIR_ALGORITHMS.get(slot);
            var recoveryTarget = targetSlotFor(slot, state.orientation());
            if (state.protectedSlots().contains(recoveryTarget)
                    || isTargetSlotSolved(state.cube(), recoveryTarget)) {
                continue;
            }
            var trialCube = state.cube().copy();
            var trialOrientation = F2LStateCodec.executeAndReturnOrientation(
                    trialCube, state.orientation(), recovery.getMoves()
            );
            if (!areProtectedTargetsSolved(
                    trialCube, targetCrossForOrientation(trialOrientation), state.protectedSlots()
            )) {
                continue;
            }
            var route = findBestInsertDatabaseSlotSolution(
                    trialCube, trialOrientation, targetSlots, state.protectedSlots()
            );
            if (route.isEmpty()) {
                route = findBestSetupThenInsertDatabaseSlotSolution(
                        trialCube, trialOrientation, targetSlots, state.protectedSlots()
                );
            }
            route.ifPresent(solution -> candidates.add(new PhaseSlotSolution(
                    solution.targetSlot(), Algorithm.normalize(recovery.concat(solution.algorithm())), true,
                    trialCube.copy(), trialOrientation.copy()
            )));
        }
        candidates.sort(PHASE_SOLUTION_COMPARATOR);
        return distinctPhaseSolutions(candidates);
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
        return findPrefixedSetupDatabaseSolutions(
                cube, orientation, new TargetSlot[]{targetSlot}, protectedSlots, checkpoint
        );
    }

    /**
     * Setup lookup is performed once for the whole unresolved-pair set. The
     * target slots are only signature views; they are not insertion targets.
     */
    private List<Algorithm> findPrefixedSetupDatabaseSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
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
            var prefixedOrientation = F2LStateCodec.executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
            for (var targetSlot : targetSlots) {
                if (protectedSlots.contains(targetSlot) || isTargetSlotSolved(prefixedCube, targetSlot)) {
                    continue;
                }
                var matches = findSetupDatabaseSolutions(
                        prefixedCube,
                        prefixedOrientation,
                        targetSlot,
                        protectedSlots
                );
                for (var match : matches) {
                    candidates.add(Algorithm.normalize(prefix.concat(match)));
                }
            }
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
            var prefixedOrientation = F2LStateCodec.executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
            var preservedMask = F2LStateCodec.preservationMaskFor(protectedSlots, prefixedOrientation);

            for (var setupCase : setupCaseDatabase.allCases()) {
                SolveCancellation.throwIfCancelled();
                if (!setupCase.preservedSlots().preservesAll(preservedMask)) {
                    continue;
                }

                var setup = Algorithm.normalize(prefix.concat(setupCase.algorithm()));
                var trialCube = cube.copy();
                var resultingOrientation = F2LStateCodec.executeAndReturnOrientation(trialCube, orientation, setup.getMoves());
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
            var prefixedOrientation = F2LStateCodec.executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
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
            var prefixedOrientation = F2LStateCodec.executeAndReturnOrientation(prefixedCube, orientation, prefix.getMoves());
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
        return findSetupDatabaseSolutions(cube, orientation, targetSlot, protectedSlots).stream().findFirst();
    }

    /**
     * A setup definition is selected by its source-state signature and by the
     * slots it promises to preserve.  Its non-preserved slot is deliberately
     * not used as an insertion-target filter: a setup can prepare any later
     * unresolved pair.
     */
    private List<Algorithm> findSetupDatabaseSolutions(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        if (setupCaseDatabase.size() == 0) {
            return List.of();
        }

        var insertSlot = visibleSlotForTarget(targetSlot, orientation);
        var preservedMask = F2LStateCodec.preservationMaskFor(protectedSlots, orientation);
        var signature = F2LSetupSignature.from(
                F2LCaseSignatureExtractor.extract(cube, insertSlot, orientation)
        );
        var candidates = new ArrayList<Algorithm>();
        for (var setupCase : setupCaseDatabase.findCompatible(preservedMask, signature).stream()
                .limit(MAX_SETUP_MATCHES_PER_VIEW)
                .toList()) {
            SolveCancellation.throwIfCancelled();
            var trialCube = cube.copy();
            var resultingOrientation = F2LStateCodec.executeAndReturnOrientation(trialCube, orientation, setupCase.algorithm().getMoves());
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
                candidates.add(setupCase.algorithm());
            }

            if (DEBUG_VERBOSE) {
                System.out.println("[F2L SETUP DB] rejected insertSlot=" + insertSlot
                        + " preserved=" + preservedMask
                        + " case=" + setupCase.name()
                        + " algorithm=" + setupCase.algorithm());
            }
        }

        candidates.sort(RAW_ALGORITHM_COMPARATOR);
        return distinctAlgorithms(candidates);
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
        var preservedMask = F2LStateCodec.preservationMaskFor(protectedSlots, orientation);
        var signature = F2LCaseSignatureExtractor.extract(cube, insertSlot, orientation);

        for (var insertCase : insertCaseDatabase.findCompatible(insertSlot, preservedMask, signature)) {
            SolveCancellation.throwIfCancelled();
            var trialCube = cube.copy();
            var resultingOrientation = F2LStateCodec.executeAndReturnOrientation(trialCube, orientation, insertCase.algorithm().getMoves());
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
                F2LStateCodec.preservationMaskFor(protectedSlots, orientation),
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
        for (var slot : F2LStateCodec.relevantSlots(targetSlot, protectedSlots)) {
            if (cube.cornerPerm[slot.corner().ordinal()] != slot.corner().ordinal() || cube.cornerOri[slot.corner().ordinal()] != 0) {
                unsolvedParts++;
            }
            if (cube.edgePerm[slot.edge().ordinal()] != slot.edge().ordinal() || cube.edgeOri[slot.edge().ordinal()] != 0) {
                unsolvedParts++;
            }
        }
        return (unsolvedParts + 3) / 4;
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

    record PendingPairStep(
            Corner corner,
            Edge edge,
            F2LSlot targetSlot,
            List<Move> completeMoves,
            CubeStateSnapshot stateBefore,
            CubeOrientationKey orientationBefore,
            F2LPreservationMask preservedSlots,
            F2LCaseDescription caseDescription,
            boolean shortestAvailable,
            boolean recoveryUsed,
            CubeStateSnapshot stateAfter,
            CubeOrientationKey orientationAfter
    ) {
        private PendingPairStep(
                Corner corner,
                Edge edge,
                F2LSlot targetSlot,
                List<Move> completeMoves,
                CubeStateSnapshot stateBefore,
                CubeOrientationKey orientationBefore,
                F2LPreservationMask preservedSlots,
                F2LCaseDescription caseDescription,
                boolean shortestAvailable,
                boolean recoveryUsed
        ) {
            this(
                    corner, edge, targetSlot, completeMoves, stateBefore, orientationBefore,
                    preservedSlots, caseDescription, shortestAvailable, recoveryUsed, null, null
            );
        }

        private PendingPairStep withAfter(CubeState cube, CubeOrientation orientation) {
            return new PendingPairStep(
                    corner, edge, targetSlot, completeMoves, stateBefore, orientationBefore,
                    preservedSlots, caseDescription, shortestAvailable, recoveryUsed,
                    CubeStateSnapshot.from(cube), CubeOrientationKey.from(orientation)
            );
        }
    }

    record PhaseSlotSolution(
            TargetSlot targetSlot,
            Algorithm algorithm,
            boolean recoveryUsed,
            CubeState caseCube,
            CubeOrientation caseOrientation
    ) {
    }

    private record RecoveryTransition(F2LSlot slot, Algorithm algorithm) {
    }

    record OptimizedState(
            CubeState cube,
            CubeOrientation orientation,
            List<TargetSlot> protectedSlots,
            Algorithm solution,
            List<PendingPairStep> pendingSteps
    ) {
    }

    record OptimizedTransition(
            Algorithm algorithm,
            CubeState cube,
            CubeOrientation orientation,
            List<TargetSlot> protectedSlots,
            List<PendingPairStep> pendingSteps
    ) {
    }

    @FunctionalInterface
    interface SearchCheckpoint {
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
            boolean optimizationBudgetExpired,
            long generatedTransitions,
            long rejectedTransitions,
            int frontierSize,
            int maxFrontierSize,
            int maxSearchDepth,
            boolean searchLimitReached,
            long insertLookupNanos,
            long setupLookupNanos,
            long validationNanos
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
                    "", 0, 0, 0, 0, false, 0, 0, 0, 0, 0, false, 0, 0, 0);
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
                    totalCrosses, optimizationCandidate, totalOptimizationCandidates, optimizationBudgetExpired,
                    generatedTransitions, rejectedTransitions, frontierSize, maxFrontierSize,
                    maxSearchDepth, searchLimitReached,
                    insertLookupNanos, setupLookupNanos, validationNanos
            );
        }
    }

    public record F2LCandidate(
            Algorithm algorithm,
            CubeState cube,
            CubeOrientation orientation,
            F2LSolveTrace trace
    ) {
        public F2LCandidate {
            if (algorithm == null || cube == null || orientation == null || trace == null) {
                throw new IllegalArgumentException("optimized candidate values cannot be null");
            }
        }
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
