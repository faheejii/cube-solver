package solver;

import algorithms.F2LCaseDatabase;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry.SlotPair;
import cfop.F2LGeometry.TargetSlot;
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
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import static cfop.F2LGeometry.ensureCrossSolved;
import static cfop.F2LGeometry.isPairConnected;
import static cfop.F2LGeometry.isPairSolved;
import static cfop.F2LGeometry.isTargetCrossSolved;
import static cfop.F2LGeometry.isTargetSlotSolved;
import static cfop.F2LGeometry.slotIndexForPair;
import static cfop.F2LGeometry.slotPairsForTargets;
import static cfop.F2LGeometry.targetCrossForOrientation;
import static cfop.F2LGeometry.targetSlotFor;
import static cfop.F2LGeometry.targetSlotForPair;
import static cfop.F2LGeometry.targetSlotsForOrientation;
import static cfop.F2LGeometry.visibleSlotForStageIndex;

public class F2LSolver {
    private static final boolean DEBUG_DB = Boolean.getBoolean("f2l.debug");
    private static final boolean DEBUG_VERBOSE = Boolean.getBoolean("f2l.debug.verbose");
    private static final int MAX_SLOT_SEARCH_DEPTH = 12;

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
    private static final Algorithm[] SEARCH_PREFIX_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME))
    };
    private final F2LCaseDatabase caseDatabase;

    public F2LSolver() {
        this(null);
    }

    public F2LSolver(F2LCaseDatabase caseDatabase) {
        this.caseDatabase = caseDatabase;
        if (this.caseDatabase != null) {
            this.caseDatabase.validate();
        }
    }

    public Algorithm solve(CubeState cube) {
        return solveStage(copyCube(cube), new CubeOrientation());
    }

    public Algorithm solveAfterCross(CubeState cube, Face crossFace) {
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveStage(copyCube(cube), OrientationFrames.orientedFrameFor(crossFace)));
    }

    public Algorithm solve(OrientedCube cube) {
        return solveStage(copyCube(cube.cubeState()), cube.orientation());
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveStage(copyCube(cube), OrientationFrames.orientedFrameFor(crossFace)));
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot) {
        return solveSlot(cube, slot, Face.D);
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot, Face crossFace) {
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        return OrientationFrames.orientationToD(crossFace).concat(
                solveSlotInternal(
                        copyCube(cube),
                        orientation,
                        targetCrossForOrientation(orientation),
                        targetSlotFor(slot, orientation),
                        List.of(),
                        mapFaceTurns(orientation)
                )
        );
    }

    private Algorithm solveForTargets(CubeState cube, CubeOrientation orientation) {
        var targetCross = targetCrossForOrientation(orientation);
        var targetSlots = targetSlotsForOrientation(orientation);
        var faceTurns = mapFaceTurns(orientation);
        var workingCube = copyCube(cube);
        ensureCrossSolved(workingCube, targetCross);

        var solution = new Algorithm();
        var protectedSlots = new ArrayList<TargetSlot>();

        for (var targetSlot : targetSlots) {
            if (isTargetSlotSolved(workingCube, targetSlot)) {
                protectedSlots.add(targetSlot);
                continue;
            }

            var slotSolution = solveSlotInternal(workingCube, orientation, targetCross, targetSlot, protectedSlots, faceTurns);
            executeMovesInOrientation(workingCube, orientation, slotSolution.getMoves());
            solution = solution.concat(slotSolution);
            protectedSlots.add(targetSlot);
        }

        return solution;
    }

    private Algorithm solveStage(CubeState cube, CubeOrientation initialOrientation) {
        if (hasCaseDatabase()) {
            return solveStageWithDatabase(cube, initialOrientation);
        }

        return solveForTargets(
                cube,
                initialOrientation
        );
    }

    private Algorithm solveStageWithDatabase(CubeState cube, CubeOrientation initialOrientation) {
        var workingCube = copyCube(cube);
        var stageTargets = targetSlotsForOrientation(initialOrientation);
        var stagePairs = slotPairsForTargets(stageTargets);

        ensureCrossSolved(workingCube, targetCrossForOrientation(initialOrientation));
        return solveStageWithDatabaseRecursive(workingCube, initialOrientation.copy(), stagePairs, stageTargets, new ArrayList<>(), 0);
    }

    private Algorithm solveStageWithDatabaseRecursive(
            CubeState cube,
            CubeOrientation currentOrientation,
            SlotPair[] stagePairs,
            TargetSlot[] stageTargets,
            List<SlotPair> protectedPairs,
            int depth
    ) {
        var nextProtected = new ArrayList<>(protectedPairs);
        boolean foundUnsolved = false;

        if (DEBUG_DB) {
            System.out.println("[F2L STAGE] depth=" + depth
                    + " orientation=" + currentOrientation
                    + " protected=" + nextProtected);
        }

        for (var pair : stagePairs) {
            if (nextProtected.contains(pair)) {
                continue;
            }
            if (isPairSolved(cube, pair, stagePairs, stageTargets, currentOrientation)) {
                nextProtected.add(pair);
                continue;
            }
            foundUnsolved = true;
        }

        if (!foundUnsolved) {
            if (DEBUG_DB) {
                System.out.println("[F2L STAGE] depth=" + depth + " complete");
            }
            return new Algorithm();
        }

        List<CandidateSolution> candidates;
        try {
            candidates = findDatabaseCandidates(cube, currentOrientation, stagePairs, stageTargets, nextProtected);
        } catch (IllegalStateException ignored) {
            if (DEBUG_DB) {
                System.out.println("[F2L STAGE] depth=" + depth + " no DB candidate, falling back to one-slot search");
            }
            var fallback = findOneSlotSearchFallback(cube, currentOrientation, stagePairs, stageTargets, nextProtected);
            var nextCube = copyCube(cube);
            executeMovesInOrientation(nextCube, currentOrientation, fallback.algorithm().getMoves());
            var nextPairs = new ArrayList<>(nextProtected);
            nextPairs.add(fallback.solvedPair());
            if (DEBUG_DB) {
                System.out.println("[F2L STAGE] depth=" + depth
                        + " " + fallback.source()
                        + " solvedPair=" + fallback.solvedPair()
                        + " checkedSlot=" + fallback.checkedSlot()
                        + " algorithm=" + fallback.algorithm()
                        + " nextOrientation=" + fallback.resultingOrientation());
            }
            var rest = solveStageWithDatabaseRecursive(
                    nextCube,
                    fallback.resultingOrientation().copy(),
                    stagePairs,
                    stageTargets,
                    nextPairs,
                    depth + 1
            );
            return fallback.algorithm().concat(rest);
        }
        if (DEBUG_DB) {
            System.out.println("[F2L STAGE] depth=" + depth + " DB candidates=" + candidates.size());
        }
        for (var candidate : candidates) {
            var nextCube = copyCube(cube);
            executeMovesInOrientation(nextCube, currentOrientation, candidate.algorithm().getMoves());

            var nextPairs = new ArrayList<>(nextProtected);
            nextPairs.add(candidate.solvedPair());

            try {
                if (DEBUG_DB) {
                    System.out.println("[F2L STAGE] depth=" + depth
                            + " trying " + candidate.source()
                            + " checkedSlot=" + candidate.checkedSlot()
                            + " solvedPair=" + candidate.solvedPair()
                            + " case=" + candidate.caseName()
                            + " algorithm=" + candidate.algorithm()
                            + " nextOrientation=" + candidate.resultingOrientation());
                }
                var rest = solveStageWithDatabaseRecursive(
                    nextCube,
                    candidate.resultingOrientation().copy(),
                    stagePairs,
                    stageTargets,
                    nextPairs,
                    depth + 1
                );
                return candidate.algorithm().concat(rest);
            } catch (IllegalStateException ignored) {
                if (DEBUG_DB) {
                    System.out.println("[F2L STAGE] depth=" + depth
                            + " " + candidate.source()
                            + " branch failed for solvedPair=" + candidate.solvedPair()
                            + " checkedSlot=" + candidate.checkedSlot()
                            + " case=" + candidate.caseName()
                            + " algorithm=" + candidate.algorithm());
                }
            }
        }

        if (DEBUG_DB) {
            System.out.println("[F2L STAGE] depth=" + depth + " all DB branches failed, falling back to one-slot search");
        }
        var fallback = findOneSlotSearchFallback(cube, currentOrientation, stagePairs, stageTargets, nextProtected);
        var nextCube = copyCube(cube);
        executeMovesInOrientation(nextCube, currentOrientation, fallback.algorithm().getMoves());
        var nextPairs = new ArrayList<>(nextProtected);
        nextPairs.add(fallback.solvedPair());
        if (DEBUG_DB) {
            System.out.println("[F2L STAGE] depth=" + depth
                    + " " + fallback.source()
                    + " solvedPair=" + fallback.solvedPair()
                    + " checkedSlot=" + fallback.checkedSlot()
                    + " algorithm=" + fallback.algorithm()
                    + " nextOrientation=" + fallback.resultingOrientation());
        }
        var rest = solveStageWithDatabaseRecursive(
                nextCube,
                fallback.resultingOrientation().copy(),
                stagePairs,
                stageTargets,
                nextPairs,
                depth + 1
        );
        return fallback.algorithm().concat(rest);
    }

    private List<CandidateSolution> findDatabaseCandidates(
            CubeState cube,
            CubeOrientation stageOrientation,
            SlotPair[] stagePairs,
            TargetSlot[] stageTargets,
            List<SlotPair> protectedPairs
    ) {
        var candidates = new ArrayList<CandidateSolution>();

        for (var prefix : DB_PREFIX_TRIALS) {
            for (var pair : stagePairs) {
                if (DEBUG_VERBOSE) {
                    System.out.println("[F2L SLOT] pair=" + pair
                            + " visibleSlot=" + visibleSlotForStageIndex(
                            slotIndexForPair(pair, stagePairs),
                            stageTargets,
                            stageOrientation
                    )
                            + " prefix=" + printableAlgorithm(prefix) + " ---");
                }
                var candidate = tryDatabaseCandidate(cube, stageOrientation, stagePairs, stageTargets, protectedPairs, pair, prefix);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        candidates.sort(Comparator.comparingInt(left -> left.algorithm().getMoves().size()));

        if (!candidates.isEmpty()) {
            return candidates;
        }
        if (DEBUG_DB) {
            System.out.println("[F2L DB] no candidates for protected=" + protectedPairs
                    + " orientation=" + stageOrientation);
        }
        throw new IllegalStateException("No solvable F2L DB slot found");
    }

    private CandidateSolution tryDatabaseCandidate(
            CubeState cube,
            CubeOrientation stageOrientation,
            SlotPair[] stagePairs,
            TargetSlot[] stageTargets,
            List<SlotPair> protectedPairs,
            SlotPair targetPair,
            Algorithm prefix
    ) {
        var prefixedCube = copyCube(cube);
        var candidateOrientation = executeAndReturnOrientation(prefixedCube, stageOrientation, prefix.getMoves());
        if (protectedPairs.contains(targetPair) || isPairSolved(prefixedCube, targetPair, stagePairs, stageTargets, candidateOrientation)) {
            if (DEBUG_VERBOSE) {
                System.out.println("[F2L DB] skip pair=" + targetPair + " reason=already protected or solved");
            }
            return null;
        }
        var targetSlot = visibleSlotForStageIndex(
                slotIndexForPair(targetPair, stagePairs),
                stageTargets,
                candidateOrientation
        );
        var signature = F2LCaseSignatureExtractor.extract(prefixedCube, targetSlot, candidateOrientation);
        var match = caseDatabase.find(targetSlot, signature);
        if (match.isEmpty()) {
            if (DEBUG_VERBOSE) {
                System.out.println("[F2L SLOT] exact DB miss pair=" + targetPair
                        + " visibleSlot=" + targetSlot
                        + " prefix=" + printableAlgorithm(prefix)
                        + " signature=" + signature);
            }
            return null;
        }

        var dbCase = match.get();
        var algorithm = prefix.concat(dbCase.algorithm());
        var validation = validateTargets(cube, stageOrientation, stagePairs, stageTargets, protectedPairs, algorithm);
        if (DEBUG_VERBOSE) {
            System.out.println("[F2L SLOT] exact DB lookup pair=" + targetPair
                    + " visibleSlot=" + targetSlot
                    + " prefix=" + printableAlgorithm(prefix)
                    + " case=" + dbCase.name()
                    + " algorithm=" + algorithm);
        }
        if (!validation.valid() || validation.solvedPair() == null) {
            if (DEBUG_VERBOSE) {
                System.out.println("[F2L SLOT] exact DB rejected pair=" + targetPair
                        + " visibleSlot=" + targetSlot
                        + " prefix=" + printableAlgorithm(prefix)
                        + " case=" + dbCase.name()
                        + " reason=" + validation.reason()
                        + " algorithm=" + algorithm);
            }
            return null;
        }
        if (DEBUG_DB) {
            System.out.println("[F2L SLOT] exact DB accepted pair=" + targetPair
                    + " visibleSlot=" + targetSlot
                    + " solvedPair=" + validation.solvedPair()
                    + " prefix=" + printableAlgorithm(prefix)
                    + " case=" + dbCase.name()
                    + " algorithm=" + algorithm);
        }

        return new CandidateSolution(algorithm, validation.resultingOrientation(), validation.solvedPair(), "exact DB lookup", targetSlot, dbCase.name());
    }

    private CandidateSolution findOneSlotSearchFallback(
            CubeState cube,
            CubeOrientation currentOrientation,
            SlotPair[] stagePairs,
            TargetSlot[] stageTargets,
            List<SlotPair> protectedPairs
    ) {
        CandidateSolution best = null;

        for (var prefix : SEARCH_PREFIX_TRIALS) {
            var prefixedCube = copyCube(cube);
            var resultingOrientation = executeAndReturnOrientation(prefixedCube, currentOrientation, prefix.getMoves());
            var protectedTargets = new ArrayList<TargetSlot>();
            for (var protectedPair : protectedPairs) {
                protectedTargets.add(targetSlotForPair(protectedPair, stagePairs, stageTargets, resultingOrientation));
            }

            for (var pair : stagePairs) {
                if (protectedPairs.contains(pair) || isPairSolved(prefixedCube, pair, stagePairs, stageTargets, resultingOrientation)) {
                    continue;
                }

                try {
                    var checkedSlot = visibleSlotForStageIndex(
                            slotIndexForPair(pair, stagePairs),
                            stageTargets,
                            resultingOrientation
                    );
                    var targetSlot = targetSlotForPair(pair, stagePairs, stageTargets, resultingOrientation);
                    var slotSolution = solveSlotInternal(
                            prefixedCube,
                            resultingOrientation,
                            targetCrossForOrientation(resultingOrientation),
                            targetSlot,
                            protectedTargets,
                            mapFaceTurns(resultingOrientation)
                    );
                    var total = prefix.concat(slotSolution);
                    if (DEBUG_VERBOSE) {
                        System.out.println("[F2L SLOT] IDA/search candidate pair=" + pair
                                + " checkedSlot=" + checkedSlot
                                + " prefix=" + printableAlgorithm(prefix)
                                + " algorithm=" + total);
                    }
                    if (best == null || total.getMoves().size() < best.algorithm().getMoves().size()) {
                        best = new CandidateSolution(total, resultingOrientation.copy(), pair, "IDA/search brute force", checkedSlot, "<none>");
                    }
                } catch (IllegalStateException ignored) {
                    if (DEBUG_VERBOSE) {
                        System.out.println("[F2L SLOT] IDA/search miss pair=" + pair
                                + " prefix=" + printableAlgorithm(prefix));
                    }
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("Failed to solve any remaining F2L slot");
        }
        if (DEBUG_DB) {
            System.out.println("[F2L SLOT] IDA/search selected solvedPair=" + best.solvedPair()
                    + " checkedSlot=" + best.checkedSlot()
                    + " case=" + best.caseName()
                    + " algorithm=" + best.algorithm()
                    + " nextOrientation=" + best.resultingOrientation());
        }
        return best;
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
        var setup = solveSetupPhase(cube, orientation, targetCross, targetPair, targetSlot, protectedSlots, faceTurns);
        var setupCube = copyCube(cube);
        applyMappedMoves(setupCube, setup.getMoves(), faceTurns);

        var insert = solveInsertPhase(setupCube, targetCross, targetSlot, protectedSlots, faceTurns);
        return setup.concat(insert);
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
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            Move[] faceTurns
    ) {
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

    private Algorithm solveSearchPhase(
            CubeState cube,
            Move[] faceTurns,
            String phaseName,
            SearchGoal goal
    ) {
        var path = new ArrayList<Move>();
        int bound = goal.heuristic(cube);
        while (bound <= MAX_SLOT_SEARCH_DEPTH) {
            var outcome = idaSearch(
                    copyCube(cube),
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

            var nextCube = copyCube(cube);
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
        if (!isTargetCrossSolved(cube, targetCross)) {
            return false;
        }
        if (!isTargetSlotSolved(cube, targetSlot)) {
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

    private static CubeState copyCube(CubeState cube) {
        var copy = new CubeState();
        copy.cornerPerm = Arrays.copyOf(cube.cornerPerm, cube.cornerPerm.length);
        copy.cornerOri = Arrays.copyOf(cube.cornerOri, cube.cornerOri.length);
        copy.edgePerm = Arrays.copyOf(cube.edgePerm, cube.edgePerm.length);
        copy.edgeOri = Arrays.copyOf(cube.edgeOri, cube.edgeOri.length);
        return copy;
    }

    private boolean hasCaseDatabase() {
        return caseDatabase != null && caseDatabase.size() > 0;
    }

    private static ValidationResult validateTargets(
            CubeState cube,
            CubeOrientation stageOrientation,
            SlotPair[] stagePairs,
            TargetSlot[] stageTargets,
            List<SlotPair> protectedPairs,
            Algorithm algorithm
    ) {
        var trialCube = copyCube(cube);
        var resultingOrientation = executeAndReturnOrientation(trialCube, stageOrientation, algorithm.getMoves());

        if (!isTargetCrossSolved(trialCube, targetCrossForOrientation(resultingOrientation))) {
            return new ValidationResult(false, "cross not solved in resulting orientation", null, null);
        }
        for (var protectedPair : protectedPairs) {
            if (!isPairSolved(trialCube, protectedPair, stagePairs, stageTargets, resultingOrientation)) {
                return new ValidationResult(false, "protected pair not solved: " + protectedPair + " in resulting orientation", null, null);
            }
        }
        SlotPair solvedPair = null;
        for (var pair : stagePairs) {
            if (protectedPairs.contains(pair)) {
                continue;
            }
            if (isPairSolved(trialCube, pair, stagePairs, stageTargets, resultingOrientation)) {
                solvedPair = pair;
                break;
            }
        }
        if (solvedPair == null) {
            return new ValidationResult(false, "no new pair solved in resulting orientation", null, null);
        }
        return new ValidationResult(true, null, solvedPair, resultingOrientation);
    }

    private static void executeMovesInOrientation(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var orientedCube = new OrientedCube(cube, orientation);
        orientedCube.applyMoves(moves);
    }

    private static CubeOrientation executeAndReturnOrientation(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var orientedCube = new OrientedCube(cube, orientation);
        orientedCube.applyMoves(moves);
        return orientedCube.orientation();
    }

    private static void applyMappedMoves(CubeState cube, List<Move> moves, Move[] faceTurns) {
        for (var move : moves) {
            MoveApplier.applyMove(cube, mappedFaceTurn(move, faceTurns));
        }
    }

    private static Move mappedFaceTurn(Move move, Move[] faceTurns) {
        for (int i = 0; i < FACE_TURNS.length; i++) {
            if (FACE_TURNS[i] == move) {
                return faceTurns[i];
            }
        }
        throw new IllegalArgumentException("Unexpected F2L search move: " + move);
    }

    private static String printableAlgorithm(Algorithm algorithm) {
        return algorithm.isEmpty() ? "<none>" : algorithm.toString();
    }

    private record CandidateSolution(
            Algorithm algorithm,
            CubeOrientation resultingOrientation,
            SlotPair solvedPair,
            String source,
            F2LSlot checkedSlot,
            String caseName
    ) {
    }

    private record ValidationResult(boolean valid, String reason, SlotPair solvedPair, CubeOrientation resultingOrientation) {
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
