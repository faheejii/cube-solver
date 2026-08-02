package solver;

import cfop.F2LSlot;
import cfop.F2LPreservationMask;
import cube.Algorithm;
import cube.Corner;
import cube.CubeOrientationKey;
import cube.CubeStateSnapshot;
import cube.Edge;
import cube.Move;

import java.util.List;

public record F2LPairStep(
        int order,
        Corner corner,
        Edge edge,
        F2LSlot targetSlot,
        List<Move> setupMoves,
        List<Move> pairingMoves,
        List<Move> insertionMoves,
        List<Move> completeMoves,
        boolean moveBreakdownAvailable,
        CubeStateSnapshot stateBefore,
        CubeOrientationKey orientationBefore,
        CubeStateSnapshot stateAfter,
        CubeOrientationKey orientationAfter,
        F2LPreservationMask preservedSlots,
        F2LCaseDescription caseDescription,
        F2LSelectionEvidence selectionEvidence
) {
    public F2LPairStep {
        if (order < 1) {
            throw new IllegalArgumentException("order must be positive");
        }
        if (corner == null || edge == null || targetSlot == null) {
            throw new IllegalArgumentException("pair identity cannot be null");
        }
        setupMoves = immutableMoves(setupMoves, "setupMoves");
        pairingMoves = immutableMoves(pairingMoves, "pairingMoves");
        insertionMoves = immutableMoves(insertionMoves, "insertionMoves");
        completeMoves = immutableMoves(completeMoves, "completeMoves");
        if (stateBefore == null || orientationBefore == null || stateAfter == null || orientationAfter == null) {
            throw new IllegalArgumentException("pair states and orientations cannot be null");
        }
        if (preservedSlots == null || caseDescription == null || selectionEvidence == null) {
            throw new IllegalArgumentException("pair metadata cannot be null");
        }
        if (moveBreakdownAvailable && !concat(setupMoves, pairingMoves, insertionMoves).equals(completeMoves)) {
            throw new IllegalArgumentException("available move breakdown must equal completeMoves");
        }
    }

    public Algorithm completeAlgorithm() {
        return Algorithm.fromMoves(completeMoves);
    }

    public int moveCount() {
        return completeAlgorithm().getMoveCount();
    }

    public List<Move> breakdownMoves() {
        return List.copyOf(concat(setupMoves, pairingMoves, insertionMoves));
    }

    private static List<Move> immutableMoves(List<Move> moves, String name) {
        if (moves == null || moves.stream().anyMatch(move -> move == null)) {
            throw new IllegalArgumentException(name + " cannot be null or contain null moves");
        }
        return List.copyOf(moves);
    }

    private static List<Move> concat(List<Move> first, List<Move> second, List<Move> third) {
        var result = new java.util.ArrayList<Move>(first.size() + second.size() + third.size());
        result.addAll(first);
        result.addAll(second);
        result.addAll(third);
        return result;
    }
}
