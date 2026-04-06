package solver;

import algorithms.F2LCaseDatabase;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class F2LSolver {
    private static final boolean DEBUG_DB = false;
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
    private static final F2LSlot[] SLOT_ORDER = {F2LSlot.FR, F2LSlot.FL, F2LSlot.BL, F2LSlot.BR};
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

            var slotSolution = solveSlotInternal(workingCube, targetCross, targetSlot, protectedSlots, faceTurns);
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
                        + " search solvedPair=" + fallback.solvedPair()
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
                            + " trying DB solvedPair=" + candidate.solvedPair()
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
                            + " DB branch failed for solvedPair=" + candidate.solvedPair()
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
                    + " search solvedPair=" + fallback.solvedPair()
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
                if (DEBUG_DB) {
                    System.out.println("[F2L DB] --- pair=" + pair
                            + " prefix=" + printableAlgorithm(prefix) + " ---");
                }
                var candidate = tryDatabaseCandidate(cube, stageOrientation, stagePairs, stageTargets, protectedPairs, pair, prefix);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        candidates.sort((left, right) -> Integer.compare(
                left.algorithm().getMoves().size(),
                right.algorithm().getMoves().size()
        ));

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
            if (DEBUG_DB) {
                System.out.println("[F2L DB] skip pair=" + targetPair + " reason=already protected or solved");
            }
            return null;
        }
        for (var dbCase : caseDatabase.allCases()) {
            var algorithm = prefix.concat(dbCase.algorithm());
            var validation = validateTargets(cube, stageOrientation, stagePairs, stageTargets, protectedPairs, algorithm);
            if (DEBUG_DB) {
                System.out.println("[F2L DB] pair=" + targetPair
                        + " prefix=" + printableAlgorithm(prefix)
                        + " case=" + dbCase.name()
                        + " algorithm=" + algorithm);
            }
            if (!validation.valid() || validation.solvedPair() == null) {
                if (DEBUG_DB) {
                    System.out.println("[F2L DB] rejected pair=" + targetPair
                            + " prefix=" + printableAlgorithm(prefix)
                            + " case=" + dbCase.name()
                            + " reason=" + validation.reason()
                            + " algorithm=" + algorithm);
                }
                continue;
            }
            if (DEBUG_DB) {
                System.out.println("[F2L DB] accepted pair=" + targetPair
                        + " solvedPair=" + validation.solvedPair()
                        + " prefix=" + printableAlgorithm(prefix)
                        + " case=" + dbCase.name()
                        + " algorithm=" + algorithm);
            }

            return new CandidateSolution(algorithm, validation.resultingOrientation(), validation.solvedPair());
        }

        if (DEBUG_DB) {
            System.out.println("[F2L DB] no match for pair=" + targetPair + " prefix=" + printableAlgorithm(prefix));
        }
        return null;
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
                    var targetSlot = targetSlotForPair(pair, stagePairs, stageTargets, resultingOrientation);
                    var slotSolution = solveSlotInternal(
                            prefixedCube,
                            targetCrossForOrientation(resultingOrientation),
                            targetSlot,
                            protectedTargets,
                            mapFaceTurns(resultingOrientation)
                    );
                    var total = prefix.concat(slotSolution);
                    if (DEBUG_DB) {
                        System.out.println("[F2L SEARCH] solved pair=" + pair
                                + " prefix=" + printableAlgorithm(prefix)
                                + " algorithm=" + total);
                    }
                    if (best == null || total.getMoves().size() < best.algorithm().getMoves().size()) {
                        best = new CandidateSolution(total, resultingOrientation.copy(), pair);
                    }
                } catch (IllegalStateException ignored) {
                    if (DEBUG_DB) {
                        System.out.println("[F2L SEARCH] no solution for pair=" + pair
                                + " prefix=" + printableAlgorithm(prefix));
                    }
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("Failed to solve any remaining F2L slot");
        }
        if (DEBUG_DB) {
            System.out.println("[F2L SEARCH] best solvedPair=" + best.solvedPair()
                    + " algorithm=" + best.algorithm()
                    + " nextOrientation=" + best.resultingOrientation());
        }
        return best;
    }

    private Algorithm solveSlotInternal(
            CubeState cube,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            Move[] faceTurns
    ) {
        ensureCrossSolved(cube, targetCross);
        if (areGoalsSolved(cube, targetCross, targetSlot, protectedSlots)) {
            return new Algorithm();
        }

        var path = new ArrayList<Move>();
        int bound = canonicalHeuristic(cube, targetCross, targetSlot, protectedSlots);
        while (bound <= MAX_SLOT_SEARCH_DEPTH) {
            var outcome = idaSearch(
                    copyCube(cube),
                    targetCross,
                    targetSlot,
                    protectedSlots,
                    faceTurns,
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

        throw new IllegalStateException("Failed to find an F2L solution for slot " + targetSlot);
    }


    private SearchOutcome idaSearch(
            CubeState cube,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            Move[] faceTurns,
            List<Move> path,
            Move lastMove,
            int depth,
            int bound,
            Map<Long, Integer> visitedDepth
    ) {
        int estimate = depth + canonicalHeuristic(cube, targetCross, targetSlot, protectedSlots);
        if (estimate > bound) {
            return new SearchOutcome(null, estimate);
        }
        if (areGoalsSolved(cube, targetCross, targetSlot, protectedSlots)) {
            return new SearchOutcome(Algorithm.fromMoves(List.copyOf(path)), depth);
        }

        long stateKey = encodeState(cube, targetCross, targetSlot, protectedSlots);
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
                    targetCross,
                    targetSlot,
                    protectedSlots,
                    faceTurns,
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

    private static void ensureCrossSolved(CubeState cube, Edge[] targetCross) {
        if (!isTargetCrossSolved(cube, targetCross)) {
            throw new IllegalArgumentException("F2L requires a solved D cross");
        }
    }

    private static SlotPair[] slotPairsForTargets(TargetSlot[] targets) {
        return new SlotPair[]{
                new SlotPair(targets[0].corner(), targets[0].edge()),
                new SlotPair(targets[1].corner(), targets[1].edge()),
                new SlotPair(targets[2].corner(), targets[2].edge()),
                new SlotPair(targets[3].corner(), targets[3].edge())
        };
    }

    private static boolean isPairSolved(CubeState cube, SlotPair pair, SlotPair[] stagePairs, CubeOrientation orientation) {
        var targetSlot = targetSlotForPair(pair, stagePairs, targetSlotsForOrientation(orientation), orientation);
        return isTargetSlotSolved(cube, targetSlot);
    }

    private static boolean isPairSolved(CubeState cube, SlotPair pair, SlotPair[] stagePairs, TargetSlot[] stageTargets, CubeOrientation orientation) {
        var targetSlot = targetSlotForPair(pair, stagePairs, stageTargets, orientation);
        return isTargetSlotSolved(cube, targetSlot);
    }

    private static TargetSlot targetSlotForPair(SlotPair pair, SlotPair[] stagePairs, TargetSlot[] stageTargets, CubeOrientation orientation) {
        var index = slotIndexForPair(pair, stagePairs);
        var visibleSlot = visibleSlotForStageIndex(index, stageTargets, orientation);
        return targetSlotFor(visibleSlot, orientation);
    }

    private static F2LSlot visibleSlotForStageIndex(int index, TargetSlot[] stageTargets, CubeOrientation orientation) {
        var originalTarget = stageTargets[index];
        for (var slot : SLOT_ORDER) {
            var visibleTarget = targetSlotFor(slot, orientation);
            if (visibleTarget.corner() == originalTarget.corner() && visibleTarget.edge() == originalTarget.edge()) {
                return slot;
            }
        }
        throw new IllegalStateException("Missing visible slot for stage index " + index);
    }

    private static TargetSlot[] targetSlotsForOrientation(CubeOrientation orientation) {
        return new TargetSlot[]{
                targetSlotFor(F2LSlot.FR, orientation),
                targetSlotFor(F2LSlot.FL, orientation),
                targetSlotFor(F2LSlot.BL, orientation),
                targetSlotFor(F2LSlot.BR, orientation)
        };
    }

    private static TargetSlot targetSlotFor(F2LSlot slot, CubeOrientation orientation) {
        return switch (slot) {
            case FR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.R))
            );
            case FL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.L))
            );
            case BL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.L))
            );
            case BR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.R))
            );
        };
    }

    private static Edge[] targetCrossForOrientation(CubeOrientation orientation) {
        return new Edge[]{
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.R)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.L))
        };
    }

    private static Move[] mapFaceTurns(CubeOrientation orientation) {
        var mapped = new Move[FACE_TURNS.length];
        for (int i = 0; i < FACE_TURNS.length; i++) {
            mapped[i] = orientation.mapMove(FACE_TURNS[i]);
        }
        return mapped;
    }

    private static boolean isTargetCrossSolved(CubeState cube, Edge[] targetCross) {
        for (var edge : targetCross) {
            if (cube.edgePerm[edge.ordinal()] != edge.ordinal() || cube.edgeOri[edge.ordinal()] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTargetSlotSolved(CubeState cube, TargetSlot slot) {
        return cube.cornerPerm[slot.corner().ordinal()] == slot.corner().ordinal()
                && cube.cornerOri[slot.corner().ordinal()] == 0
                && cube.edgePerm[slot.edge().ordinal()] == slot.edge().ordinal()
                && cube.edgeOri[slot.edge().ordinal()] == 0;
    }

    private static Edge edgeForFaces(Face first, Face second) {
        if (matches(first, second, Face.U, Face.R)) return Edge.UR;
        if (matches(first, second, Face.U, Face.F)) return Edge.UF;
        if (matches(first, second, Face.U, Face.L)) return Edge.UL;
        if (matches(first, second, Face.U, Face.B)) return Edge.UB;
        if (matches(first, second, Face.D, Face.R)) return Edge.DR;
        if (matches(first, second, Face.D, Face.F)) return Edge.DF;
        if (matches(first, second, Face.D, Face.L)) return Edge.DL;
        if (matches(first, second, Face.D, Face.B)) return Edge.DB;
        if (matches(first, second, Face.F, Face.R)) return Edge.FR;
        if (matches(first, second, Face.F, Face.L)) return Edge.FL;
        if (matches(first, second, Face.B, Face.R)) return Edge.BR;
        if (matches(first, second, Face.B, Face.L)) return Edge.BL;
        throw new IllegalArgumentException("Faces do not form an edge: " + first + ", " + second);
    }

    private static Corner cornerForFaces(Face a, Face b, Face c) {
        if (matchesAll(a, b, c, Face.U, Face.R, Face.F)) return Corner.URF;
        if (matchesAll(a, b, c, Face.U, Face.F, Face.L)) return Corner.UFL;
        if (matchesAll(a, b, c, Face.U, Face.L, Face.B)) return Corner.ULB;
        if (matchesAll(a, b, c, Face.U, Face.B, Face.R)) return Corner.UBR;
        if (matchesAll(a, b, c, Face.D, Face.F, Face.R)) return Corner.DFR;
        if (matchesAll(a, b, c, Face.D, Face.L, Face.F)) return Corner.DLF;
        if (matchesAll(a, b, c, Face.D, Face.B, Face.L)) return Corner.DBL;
        if (matchesAll(a, b, c, Face.D, Face.R, Face.B)) return Corner.DRB;
        throw new IllegalArgumentException("Faces do not form a corner: " + a + ", " + b + ", " + c);
    }

    private static boolean matches(Face first, Face second, Face expectedA, Face expectedB) {
        return (first == expectedA && second == expectedB) || (first == expectedB && second == expectedA);
    }

    private static boolean matchesAll(Face a, Face b, Face c, Face x, Face y, Face z) {
        return contains(a, b, c, x) && contains(a, b, c, y) && contains(a, b, c, z);
    }

    private static boolean contains(Face a, Face b, Face c, Face target) {
        return a == target || b == target || c == target;
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

    private static String printableAlgorithm(Algorithm algorithm) {
        return algorithm.isEmpty() ? "<none>" : algorithm.toString();
    }

    private static int slotIndexForPair(SlotPair pair, SlotPair[] stagePairs) {
        for (int i = 0; i < stagePairs.length; i++) {
            if (stagePairs[i].equals(pair)) {
                return i;
            }
        }
        throw new IllegalStateException("Missing stage slot for pair " + pair);
    }

    private record TargetSlot(Corner corner, Edge edge) {
    }

    private record SlotPair(Corner cornerPiece, Edge edgePiece) {
    }

    private record CandidateSolution(Algorithm algorithm, CubeOrientation resultingOrientation, SlotPair solvedPair) {
    }

    private record ValidationResult(boolean valid, String reason, SlotPair solvedPair, CubeOrientation resultingOrientation) {
    }

    private record SearchOutcome(Algorithm solution, int nextBound) {
    }
}
