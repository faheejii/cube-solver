package solver;

import algorithms.F2LCaseDatabase;
import cfop.F2LSlot;
import cfop.F2LCaseFrame;
import cfop.F2LCaseSignatureExtractor;
import cube.Algorithm;
import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class F2LSolver {
    private static final int MAX_SLOT_SEARCH_DEPTH = 12;
    private static final int CURRENT_FRAME_ROTATION_GATE_DEPTH = 6;
    private static final int CURRENT_FRAME_ACCEPT_LENGTH = 4;

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
    private static final Algorithm[] SLOT_ROTATIONS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.Y)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y2))
    };

    private static final Edge[] D_CROSS_EDGES = {Edge.DF, Edge.DR, Edge.DB, Edge.DL};
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
        return solveStage(copyCube(cube), new Algorithm());
    }

    public Algorithm solveAfterCross(CubeState cube, Face crossFace) {
        return solveStage(copyCube(cube), orientationToD(crossFace));
    }

    public Algorithm solve(OrientedCube cube) {
        return solveForTargets(
                copyCube(cube.cubeState()),
                targetCrossForOrientation(cube.orientation()),
                targetSlotsForOrientation(cube.orientation()),
                mapFaceTurns(cube.orientation())
        );
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        var orientation = orientationToD(crossFace);
        var workingCube = copyCube(cube);
        MoveApplier.applyMoves(workingCube, orientation.getMoves());
        return orientation.concat(solveStage(workingCube, orientation));
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot) {
        return solveSlot(cube, slot, Face.D);
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot, Face crossFace) {
        var orientation = orientationToD(crossFace);
        var orientedCube = new OrientedCube(copyCube(cube));
        orientedCube.applyMoves(orientation.getMoves());

        return orientation.concat(
                solveSlotInternal(
                        orientedCube.cubeState(),
                        targetCrossForOrientation(orientedCube.orientation()),
                        targetSlotFor(slot, orientedCube.orientation()),
                        List.of(),
                        mapFaceTurns(orientedCube.orientation())
                )
        );
    }

    private Algorithm solveForTargets(CubeState cube, Edge[] targetCross, TargetSlot[] targetSlots, Move[] faceTurns) {
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
            MoveApplier.applyMoves(workingCube, slotSolution.getMoves());
            solution = solution.concat(slotSolution);
            protectedSlots.add(targetSlot);
        }

        return solution;
    }

    private Algorithm solveStage(CubeState cube, Algorithm initialFrame) {
        if (hasCaseDatabase()) {
            return solveStageWithDatabase(cube, orientationFor(initialFrame));
        }

        var workingCube = copyCube(cube);
        var protectedPairs = new ArrayList<SlotPair>();
        var solution = new Algorithm();

        ensurePlacedCrossSolved(workingCube, targetCrossPiecesForFrame(initialFrame));

        for (int i = 0; i < SLOT_ORDER.length; i++) {
            var pair = slotPairsForFrame(initialFrame)[i];
            if (isPairSolved(workingCube, pair, initialFrame)) {
                protectedPairs.add(pair);
                continue;
            }

            var candidate = findBestPlacedCandidate(workingCube, initialFrame, pair, protectedPairs);
            MoveApplier.applyMoves(workingCube, candidate.algorithm().getMoves());
            solution = solution.concat(candidate.algorithm());
            protectedPairs.add(pair);
        }

        return solution;
    }

    private Algorithm solveStageWithDatabase(CubeState cube, CubeOrientation initialOrientation) {
        var workingCube = copyCube(cube);
        var baseCross = targetCrossForOrientation(initialOrientation);

        ensurePlacedCrossSolved(workingCube, baseCross);
        return solveStageWithDatabaseRecursive(workingCube, initialOrientation.copy(), baseCross, new ArrayList<>());
    }

    private Algorithm solveStageWithDatabaseRecursive(
            CubeState cube,
            CubeOrientation currentOrientation,
            Edge[] baseCross,
            List<SlotPair> protectedPairs
    ) {
        var nextProtected = new ArrayList<>(protectedPairs);
        boolean foundUnsolved = false;

        for (var pair : slotPairsForOrientation(currentOrientation)) {
            if (nextProtected.contains(pair)) {
                continue;
            }
            if (isPairSolved(cube, pair, currentOrientation)) {
                nextProtected.add(pair);
                continue;
            }
            foundUnsolved = true;
        }

        if (!foundUnsolved) {
            return new Algorithm();
        }

        List<CandidateSolution> candidates;
        try {
            candidates = findDatabaseCandidates(cube, currentOrientation, baseCross, nextProtected);
        } catch (IllegalStateException ignored) {
            return solveWithPrefixFallbacks(cube, currentOrientation);
        }
        for (var candidate : candidates) {
            var nextCube = copyCube(cube);
            executeMovesInOrientation(nextCube, currentOrientation, candidate.algorithm().getMoves());

            var nextPairs = new ArrayList<>(nextProtected);
            nextPairs.add(candidate.solvedPair());

            try {
                var rest = solveStageWithDatabaseRecursive(
                        nextCube,
                        candidate.resultingOrientation().copy(),
                        baseCross,
                        nextPairs
                );
                return candidate.algorithm().concat(rest);
            } catch (IllegalStateException ignored) {
            }
        }

        return solveWithPrefixFallbacks(cube, currentOrientation);
    }

    private List<CandidateSolution> findDatabaseCandidates(
            CubeState cube,
            CubeOrientation stageOrientation,
            Edge[] baseCross,
            List<SlotPair> protectedPairs
    ) {
        var candidates = new ArrayList<CandidateSolution>();

        for (var prefix : DB_PREFIX_TRIALS) {
            var candidate = tryDatabaseCandidate(cube, stageOrientation, baseCross, protectedPairs, prefix);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        candidates.sort((left, right) -> Integer.compare(
                left.algorithm().getMoves().size(),
                right.algorithm().getMoves().size()
        ));

        if (!candidates.isEmpty()) {
            return candidates;
        }
        throw new IllegalStateException("No solvable F2L DB slot found");
    }

    private CandidateSolution tryDatabaseCandidate(
            CubeState cube,
            CubeOrientation stageOrientation,
            Edge[] baseCross,
            List<SlotPair> protectedPairs,
            Algorithm prefix
    ) {
        var prefixedCube = copyCube(cube);
        var candidateOrientation = executeAndReturnOrientation(prefixedCube, stageOrientation, prefix.getMoves());
        var target = targetSlotFor(F2LSlot.FR, candidateOrientation);
        var targetPair = new SlotPair(target.corner(), target.edge());
        if (protectedPairs.contains(targetPair) || isPairSolved(prefixedCube, targetPair, candidateOrientation)) {
            return null;
        }

        var signature = F2LCaseSignatureExtractor.extract(prefixedCube, targetPair.cornerPiece(), targetPair.edgePiece(), candidateOrientation);
        var match = caseDatabase.find(signature);
        if (match.isEmpty()) {
            return null;
        }

        var algorithm = prefix.concat(match.get().algorithm());
        var validation = validatePlacedTargets(cube, stageOrientation, baseCross, candidateOrientation, protectedPairs, algorithm);
        if (!validation.valid() || validation.solvedPair() == null) {
            return null;
        }

        return new CandidateSolution(algorithm, candidateOrientation, validation.solvedPair());
    }

    private Algorithm solveWithPrefixFallbacks(CubeState cube, CubeOrientation currentOrientation) {
        CandidateSolution best = null;

        for (var prefix : SEARCH_PREFIX_TRIALS) {
            var prefixedCube = copyCube(cube);
            var resultingOrientation = executeAndReturnOrientation(prefixedCube, currentOrientation, prefix.getMoves());
            try {
                var searchSolution = solve(new OrientedCube(prefixedCube, resultingOrientation));
                var total = prefix.concat(searchSolution);
                if (best == null || total.getMoves().size() < best.algorithm().getMoves().size()) {
                    best = new CandidateSolution(total, resultingOrientation, null);
                }
            } catch (IllegalStateException ignored) {
            }
        }

        if (best == null) {
            throw new IllegalStateException("Failed to solve remaining F2L slots");
        }
        return best.algorithm();
    }

    private Algorithm solveForPlacedTargets(CubeState cube, Edge[] targetCrossPieces, PlacedTargetSlot[] targetSlots, Move[] faceTurns) {
        var workingCube = copyCube(cube);
        ensurePlacedCrossSolved(workingCube, targetCrossPieces);

        var solution = new Algorithm();
        var protectedSlots = new ArrayList<PlacedTargetSlot>();

        for (var targetSlot : targetSlots) {
            if (isPlacedTargetSlotSolved(workingCube, targetSlot)) {
                protectedSlots.add(targetSlot);
                continue;
            }

            var slotSolution = solvePlacedSlotInternal(workingCube, targetCrossPieces, targetSlot, protectedSlots, faceTurns);
            MoveApplier.applyMoves(workingCube, slotSolution.getMoves());
            solution = solution.concat(slotSolution);
            protectedSlots.add(targetSlot);
        }

        return solution;
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

    private CandidateSolution findBestPlacedCandidate(
            CubeState cube,
            Algorithm stageFrame,
            SlotPair targetPair,
            List<SlotPair> protectedPairs
    ) {
        var currentFrameCandidate = tryPlacedCandidate(cube, stageFrame, targetPair, protectedPairs, new Algorithm(), CURRENT_FRAME_ROTATION_GATE_DEPTH);
        if (currentFrameCandidate != null) {
            return currentFrameCandidate;
        }

        CandidateSolution best = null;

        for (var rotation : SLOT_ROTATIONS) {
            var candidate = tryPlacedCandidate(cube, stageFrame, targetPair, protectedPairs, rotation, MAX_SLOT_SEARCH_DEPTH);
            if (candidate != null) {
                if (best == null || candidate.algorithm().getMoves().size() < best.algorithm().getMoves().size()) {
                    best = candidate;
                }
                if (rotation.getMoves().isEmpty() && candidate.algorithm().getMoves().size() <= CURRENT_FRAME_ACCEPT_LENGTH) {
                    return best;
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("Failed to find an F2L solution for pair " + targetPair);
        }

        return best;
    }
    
    private CandidateSolution tryPlacedCandidate(
            CubeState cube,
            Algorithm stageFrame,
            SlotPair targetPair,
            List<SlotPair> protectedPairs,
            Algorithm rotation,
            int maxDepth
    ) {
        var trialCube = copyCube(cube);
        MoveApplier.applyMoves(trialCube, rotation.getMoves());

        var trialFrame = stageFrame.concat(rotation);
        var targetCross = targetCrossPiecesForFrame(trialFrame);
        var targetSlot = placedTargetForPair(targetPair, trialFrame);
        var protectedTargets = List.of(placedTargetsForPairs(protectedPairs, trialFrame));

        try {
            var slotSolution = solvePlacedSlotInternal(
                    trialCube,
                    targetCross,
                    targetSlot,
                    protectedTargets,
                    FACE_TURNS,
                    maxDepth
            );
            return new CandidateSolution(remapMoves(slotSolution, rotation), null, null);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private Algorithm solvePlacedSlotInternal(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots,
            Move[] faceTurns
    ) {
        return solvePlacedSlotInternal(cube, targetCrossPieces, targetSlot, protectedSlots, faceTurns, MAX_SLOT_SEARCH_DEPTH);
    }

    private Algorithm solvePlacedSlotInternal(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots,
            Move[] faceTurns,
            int maxDepth
    ) {
        ensurePlacedCrossSolved(cube, targetCrossPieces);
        if (arePlacedGoalsSolved(cube, targetCrossPieces, targetSlot, protectedSlots)) {
            return new Algorithm();
        }

        var path = new ArrayList<Move>();
        int bound = placedHeuristic(cube, targetCrossPieces, targetSlot, protectedSlots);
        while (bound <= maxDepth) {
            var outcome = idaPlacedSearch(
                    copyCube(cube),
                    targetCrossPieces,
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

    private SearchOutcome idaPlacedSearch(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots,
            Move[] faceTurns,
            List<Move> path,
            Move lastMove,
            int depth,
            int bound,
            Map<Long, Integer> visitedDepth
    ) {
        int estimate = depth + placedHeuristic(cube, targetCrossPieces, targetSlot, protectedSlots);
        if (estimate > bound) {
            return new SearchOutcome(null, estimate);
        }
        if (arePlacedGoalsSolved(cube, targetCrossPieces, targetSlot, protectedSlots)) {
            return new SearchOutcome(Algorithm.fromMoves(List.copyOf(path)), depth);
        }

        long stateKey = encodePlacedState(cube, targetCrossPieces, targetSlot, protectedSlots);
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

            var outcome = idaPlacedSearch(
                    nextCube,
                    targetCrossPieces,
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

    private static boolean arePlacedGoalsSolved(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots
    ) {
        if (!isPlacedCrossSolved(cube, targetCrossPieces)) {
            return false;
        }
        if (!isPlacedTargetSlotSolved(cube, targetSlot)) {
            return false;
        }
        for (var slot : protectedSlots) {
            if (!isPlacedTargetSlotSolved(cube, slot)) {
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

    private static int placedHeuristic(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots
    ) {
        int unsolvedParts = 0;
        for (int i = 0; i < D_CROSS_EDGES.length; i++) {
            var position = D_CROSS_EDGES[i];
            var piece = targetCrossPieces[i];
            if (cube.edgePerm[position.ordinal()] != piece.ordinal() || cube.edgeOri[position.ordinal()] != 0) {
                unsolvedParts++;
            }
        }
        for (var slot : relevantPlacedSlots(targetSlot, protectedSlots)) {
            if (cube.cornerPerm[slot.cornerPosition().ordinal()] != slot.cornerPiece().ordinal()
                    || cube.cornerOri[slot.cornerPosition().ordinal()] != 0) {
                unsolvedParts++;
            }
            if (cube.edgePerm[slot.edgePosition().ordinal()] != slot.edgePiece().ordinal()
                    || cube.edgeOri[slot.edgePosition().ordinal()] != 0) {
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

    private static long encodePlacedState(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots
    ) {
        long key = 0L;
        int shift = 0;

        for (var edge : targetCrossPieces) {
            key |= ((long) encodeEdge(cube, edge)) << shift;
            shift += 5;
        }

        for (var slot : relevantPlacedSlots(targetSlot, protectedSlots)) {
            key |= ((long) encodeCorner(cube, slot.cornerPiece())) << shift;
            shift += 5;
            key |= ((long) encodeEdge(cube, slot.edgePiece())) << shift;
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

    private static List<PlacedTargetSlot> relevantPlacedSlots(PlacedTargetSlot targetSlot, List<PlacedTargetSlot> protectedSlots) {
        var slots = new ArrayList<PlacedTargetSlot>(protectedSlots.size() + 1);
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

    private static void ensurePlacedCrossSolved(CubeState cube, Edge[] targetCrossPieces) {
        if (!isPlacedCrossSolved(cube, targetCrossPieces)) {
            throw new IllegalArgumentException("F2L requires a solved D cross");
        }
    }

    private static Algorithm orientationToD(Face face) {
        return switch (face) {
            case D -> new Algorithm();
            case U -> Algorithm.fromMoves(List.of(Move.Z2));
            case R -> Algorithm.fromMoves(List.of(Move.Z));
            case L -> Algorithm.fromMoves(List.of(Move.Z_PRIME));
            case F -> Algorithm.fromMoves(List.of(Move.X));
            case B -> Algorithm.fromMoves(List.of(Move.X_PRIME));
        };
    }

    private static CubeOrientation orientedFrameFor(Face face) {
        var cube = new OrientedCube();
        cube.applyMoves(orientationToD(face).getMoves());
        return cube.orientation();
    }

    private static CubeOrientation orientationFor(Algorithm rotation) {
        var cube = new OrientedCube();
        cube.applyMoves(rotation.getMoves());
        return cube.orientation();
    }

    private static Edge[] targetCrossPiecesAfterCross(Face face) {
        var solved = new CubeState();
        MoveApplier.applyMoves(solved, orientationToD(face).getMoves());
        return new Edge[]{
                Edge.values()[solved.edgePerm[Edge.DF.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DR.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DB.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DL.ordinal()]]
        };
    }

    private static PlacedTargetSlot[] targetPlacedSlotsAfterCross(Face face) {
        var solved = new CubeState();
        MoveApplier.applyMoves(solved, orientationToD(face).getMoves());
        return new PlacedTargetSlot[]{
                new PlacedTargetSlot(
                        Corner.DFR,
                        Corner.values()[solved.cornerPerm[Corner.DFR.ordinal()]],
                        Edge.FR,
                        Edge.values()[solved.edgePerm[Edge.FR.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DLF,
                        Corner.values()[solved.cornerPerm[Corner.DLF.ordinal()]],
                        Edge.FL,
                        Edge.values()[solved.edgePerm[Edge.FL.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DBL,
                        Corner.values()[solved.cornerPerm[Corner.DBL.ordinal()]],
                        Edge.BL,
                        Edge.values()[solved.edgePerm[Edge.BL.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DRB,
                        Corner.values()[solved.cornerPerm[Corner.DRB.ordinal()]],
                        Edge.BR,
                        Edge.values()[solved.edgePerm[Edge.BR.ordinal()]]
                )
        };
    }

    private static Edge[] targetCrossPiecesForFrame(Algorithm frame) {
        var solved = new CubeState();
        MoveApplier.applyMoves(solved, frame.getMoves());
        return new Edge[]{
                Edge.values()[solved.edgePerm[Edge.DF.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DR.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DB.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DL.ordinal()]]
        };
    }

    private static PlacedTargetSlot[] targetPlacedSlotsForFrame(Algorithm frame) {
        var solved = new CubeState();
        MoveApplier.applyMoves(solved, frame.getMoves());
        return new PlacedTargetSlot[]{
                new PlacedTargetSlot(
                        Corner.DFR,
                        Corner.values()[solved.cornerPerm[Corner.DFR.ordinal()]],
                        Edge.FR,
                        Edge.values()[solved.edgePerm[Edge.FR.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DLF,
                        Corner.values()[solved.cornerPerm[Corner.DLF.ordinal()]],
                        Edge.FL,
                        Edge.values()[solved.edgePerm[Edge.FL.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DBL,
                        Corner.values()[solved.cornerPerm[Corner.DBL.ordinal()]],
                        Edge.BL,
                        Edge.values()[solved.edgePerm[Edge.BL.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DRB,
                        Corner.values()[solved.cornerPerm[Corner.DRB.ordinal()]],
                        Edge.BR,
                        Edge.values()[solved.edgePerm[Edge.BR.ordinal()]]
                )
        };
    }

    private static SlotPair[] slotPairsForFrame(Algorithm frame) {
        var targets = targetPlacedSlotsForFrame(frame);
        return new SlotPair[]{
                new SlotPair(targets[0].cornerPiece(), targets[0].edgePiece()),
                new SlotPair(targets[1].cornerPiece(), targets[1].edgePiece()),
                new SlotPair(targets[2].cornerPiece(), targets[2].edgePiece()),
                new SlotPair(targets[3].cornerPiece(), targets[3].edgePiece())
        };
    }

    private static SlotPair[] slotPairsForOrientation(CubeOrientation orientation) {
        var targets = targetSlotsForOrientation(orientation);
        return new SlotPair[]{
                new SlotPair(targets[0].corner(), targets[0].edge()),
                new SlotPair(targets[1].corner(), targets[1].edge()),
                new SlotPair(targets[2].corner(), targets[2].edge()),
                new SlotPair(targets[3].corner(), targets[3].edge())
        };
    }

    private static boolean isPairSolved(CubeState cube, SlotPair pair, Algorithm frame) {
        return isPlacedTargetSlotSolved(cube, placedTargetForPair(pair, frame));
    }

    private static boolean isPairSolved(CubeState cube, SlotPair pair, CubeOrientation orientation) {
        return isPlacedTargetSlotSolved(cube, placedTargetForPair(pair, orientation));
    }

    private static PlacedTargetSlot[] placedTargetsForPairs(List<SlotPair> pairs, Algorithm frame) {
        var targets = new PlacedTargetSlot[pairs.size()];
        for (int i = 0; i < pairs.size(); i++) {
            targets[i] = placedTargetForPair(pairs.get(i), frame);
        }
        return targets;
    }

    private static PlacedTargetSlot placedTargetForPair(SlotPair pair, Algorithm frame) {
        for (var target : targetPlacedSlotsForFrame(frame)) {
            if (target.cornerPiece() == pair.cornerPiece() && target.edgePiece() == pair.edgePiece()) {
                return target;
            }
        }
        throw new IllegalStateException("Missing target slot for pair " + pair);
    }

    private static PlacedTargetSlot placedTargetForPair(SlotPair pair, CubeOrientation orientation) {
        var slot = slotForPair(pair, orientation);
        var target = targetSlotFor(slot, orientation);
        return new PlacedTargetSlot(
                target.corner(),
                pair.cornerPiece(),
                target.edge(),
                pair.edgePiece()
        );
    }

    private static TargetSlot[] canonicalTargets() {
        return new TargetSlot[]{
                new TargetSlot(F2LSlot.FR.corner(), F2LSlot.FR.edge()),
                new TargetSlot(F2LSlot.FL.corner(), F2LSlot.FL.edge()),
                new TargetSlot(F2LSlot.BL.corner(), F2LSlot.BL.edge()),
                new TargetSlot(F2LSlot.BR.corner(), F2LSlot.BR.edge())
        };
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

    private static boolean isPlacedCrossSolved(CubeState cube, Edge[] targetCrossPieces) {
        for (int i = 0; i < D_CROSS_EDGES.length; i++) {
            var position = D_CROSS_EDGES[i];
            var piece = targetCrossPieces[i];
            if (cube.edgePerm[position.ordinal()] != piece.ordinal() || cube.edgeOri[position.ordinal()] != 0) {
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

    private static boolean isPlacedTargetSlotSolved(CubeState cube, PlacedTargetSlot slot) {
        return cube.cornerPerm[slot.cornerPosition().ordinal()] == slot.cornerPiece().ordinal()
                && cube.cornerOri[slot.cornerPosition().ordinal()] == 0
                && cube.edgePerm[slot.edgePosition().ordinal()] == slot.edgePiece().ordinal()
                && cube.edgeOri[slot.edgePosition().ordinal()] == 0;
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

    private static Algorithm remapMoves(Algorithm algorithm, Algorithm rotation) {
        if (rotation.isEmpty()) {
            return algorithm;
        }
        var orientation = orientationFor(rotation);
        var mapped = new ArrayList<Move>(algorithm.getMoves().size());
        for (var move : algorithm.getMoves()) {
            mapped.add(orientation.mapMove(move));
        }
        return Algorithm.fromMoves(mapped);
    }

    private boolean hasCaseDatabase() {
        return caseDatabase != null && caseDatabase.size() > 0;
    }

    private static ValidationResult validatePlacedTargets(
            CubeState cube,
            CubeOrientation stageOrientation,
            Edge[] baseCross,
            CubeOrientation resultingOrientation,
            List<SlotPair> protectedPairs,
            Algorithm algorithm
    ) {
        var trialCube = copyCube(cube);
        executeMovesInOrientation(trialCube, stageOrientation, algorithm.getMoves());

        if (!isPlacedCrossSolved(trialCube, baseCross)) {
            return new ValidationResult(false, "cross not solved in resulting orientation", null);
        }
        for (var protectedPair : protectedPairs) {
            if (!isPairSolved(trialCube, protectedPair, resultingOrientation)) {
                return new ValidationResult(false, "protected pair not solved: " + protectedPair + " in resulting orientation", null);
            }
        }
        SlotPair solvedPair = null;
        for (var pair : slotPairsForOrientation(resultingOrientation)) {
            if (protectedPairs.contains(pair)) {
                continue;
            }
            if (isPairSolved(trialCube, pair, resultingOrientation)) {
                solvedPair = pair;
                break;
            }
        }
        if (solvedPair == null) {
            return new ValidationResult(false, "no new pair solved in resulting orientation", null);
        }
        return new ValidationResult(true, null, solvedPair);
    }

    private static void applyRotation(CubeOrientation orientation, Algorithm algorithm) {
        for (var move : algorithm.getMoves()) {
            orientation.applyRotation(move);
        }
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

    private static Corner findCornerPosition(CubeState cube, Corner targetCorner) {
        for (var position : Corner.values()) {
            if (cube.cornerPerm[position.ordinal()] == targetCorner.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing target corner: " + targetCorner);
    }

    private static Edge findEdgePosition(CubeState cube, Edge targetEdge) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == targetEdge.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing target edge: " + targetEdge);
    }

    private static F2LSlot slotForPair(SlotPair pair, CubeOrientation orientation) {
        for (var slot : F2LSlot.values()) {
            var target = targetSlotFor(slot, orientation);
            if (target.corner() == pair.cornerPiece() && target.edge() == pair.edgePiece()) {
                return slot;
            }
        }
        throw new IllegalStateException("Missing logical slot for pair " + pair);
    }

    private record TargetSlot(Corner corner, Edge edge) {
    }

    private record PlacedTargetSlot(Corner cornerPosition, Corner cornerPiece, Edge edgePosition, Edge edgePiece) {
    }

    private record SlotPair(Corner cornerPiece, Edge edgePiece) {
    }

    private record CandidateSolution(Algorithm algorithm, CubeOrientation resultingOrientation, SlotPair solvedPair) {
    }

    private record ValidationResult(boolean valid, String reason, SlotPair solvedPair) {
    }

    private record SearchOutcome(Algorithm solution, int nextBound) {
    }
}
