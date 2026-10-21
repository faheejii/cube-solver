package solver;

import cube.Algorithm;
import cube.Move;

import java.util.List;

/** Shared move-set and canonical-order policy used by both cross search engines. */
final class CrossSearchMovePolicy {
    private static final Move[] OUTER_TURNS = {
            Move.U, Move.U2, Move.U_PRIME,
            Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME,
            Move.D, Move.D2, Move.D_PRIME,
            Move.L, Move.L2, Move.L_PRIME,
            Move.B, Move.B2, Move.B_PRIME
    };
    private static final Move[] NO_B_OUTER_TURNS = {
            Move.U, Move.U2, Move.U_PRIME,
            Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME,
            Move.D, Move.D2, Move.D_PRIME,
            Move.L, Move.L2, Move.L_PRIME
    };
    private static final List<Algorithm> FOUR_Y_TRIALS = List.of(
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.Y)),
            Algorithm.fromMoves(List.of(Move.Y2)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME))
    );

    private CrossSearchMovePolicy() {
    }

    static Move[] moves(CrossSearchOptions options) {
        return (options.avoidBTurns() ? NO_B_OUTER_TURNS : OUTER_TURNS).clone();
    }

    static List<Algorithm> fastYawTrials() {
        return FOUR_Y_TRIALS;
    }

    static List<Algorithm> advancedYawTrials(CrossSearchOptions options) {
        return options.avoidBTurns() ? FOUR_Y_TRIALS : List.of(new Algorithm());
    }

    static boolean shouldSkip(int previousFace, int currentFace) {
        if (previousFace < 0) return false;
        if (previousFace == currentFace) return true;
        if (axis(previousFace) != axis(currentFace)) return false;
        return rankOnAxis(currentFace) < rankOnAxis(previousFace);
    }

    static int faceFamily(Move move) {
        return switch (move) {
            case U, U2, U_PRIME -> 0;
            case R, R2, R_PRIME -> 1;
            case F, F2, F_PRIME -> 2;
            case D, D2, D_PRIME -> 3;
            case L, L2, L_PRIME -> 4;
            case B, B2, B_PRIME -> 5;
            default -> throw new IllegalArgumentException("Unexpected cross search move: " + move);
        };
    }

    private static int axis(int face) {
        return switch (face) {
            case 0, 3 -> 0;
            case 1, 4 -> 1;
            case 2, 5 -> 2;
            default -> throw new IllegalArgumentException("Unknown face family: " + face);
        };
    }

    private static int rankOnAxis(int face) {
        return switch (face) {
            case 0, 1, 2 -> 1;
            case 3, 4, 5 -> 0;
            default -> throw new IllegalArgumentException("Unknown face family: " + face);
        };
    }
}
