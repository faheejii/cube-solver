package cube;

import java.util.List;

public class MoveApplier {
    public static void applyMove(CubeState cube, Move move) {
        if (isCubeRotation(move)) {
            applyCubeRotation(cube, move);
            return;
        }
        if (isWideMove(move)) {
            applyWideMove(cube, move);
            return;
        }

        var m = move.ordinal();

        var newCornerPerm = new byte[8];
        var newEdgePerm = new byte[12];
        var newCornerOri = new byte[8];
        var newEdgeOri = new byte[12];

        for (int i = 0; i < 8; i++) {
            var from = MoveTables.CORNER_PERM_MOVE[m][i];
            newCornerPerm[i] = cube.cornerPerm[from];
            newCornerOri[i] = (byte) ((cube.cornerOri[from] + MoveTables.CORNER_ORI_DELTA[m][i]) % 3);
        }

        for (int i = 0; i < 12; i++) {
            var from = MoveTables.EDGE_PERM_MOVE[m][i];
            newEdgePerm[i] = cube.edgePerm[from];
            newEdgeOri[i] = (byte) ((cube.edgeOri[from] + MoveTables.EDGE_ORI_DELTA[m][i]) % 2);
        }

        cube.cornerPerm = newCornerPerm;
        cube.edgePerm = newEdgePerm;
        cube.edgeOri = newEdgeOri;
        cube.cornerOri = newCornerOri;
    }

    public static void applyMoves(CubeState cube, List<Move> moves) {
        for (var move : moves) {
            applyMove(cube, move);
        }
    }

    public static void applyAlgorithm(CubeState cube, String algorithm) {
        if (algorithm == null || algorithm.isBlank()) return;

        var parsed = Algorithm.parse(algorithm);
        for (Move move : parsed.getMoves()) {
            applyMove(cube, move);
        }
    }

    public static void executeMoves(CubeState cube, List<Move> moves) {
        var orientedCube = new OrientedCube(cube);
        orientedCube.applyMoves(moves);
    }

    public static void executeAlgorithm(CubeState cube, String algorithm) {
        if (algorithm == null || algorithm.isBlank()) return;

        var parsed = Algorithm.parse(algorithm);
        executeMoves(cube, parsed.getMoves());
    }

    private static boolean isCubeRotation(Move move) {
        return switch (move) {
            case X, X2, X_PRIME, Y, Y2, Y_PRIME, Z, Z2, Z_PRIME -> true;
            default -> false;
        };
    }

    private static boolean isWideMove(Move move) {
        return switch (move) {
            case UW, UW2, UW_PRIME,
                    RW, RW2, RW_PRIME,
                    FW, FW2, FW_PRIME,
                    DW, DW2, DW_PRIME,
                    LW, LW2, LW_PRIME,
                    BW, BW2, BW_PRIME -> true;
            default -> false;
        };
    }

    private static void applyCubeRotation(CubeState cube, Move move) {
        switch (move) {
            case X -> applyMoves(cube, List.of(Move.R, Move.M_PRIME, Move.L_PRIME));
            case X2 -> applyMoves(cube, List.of(Move.X, Move.X));
            case X_PRIME -> applyMoves(cube, List.of(Move.R_PRIME, Move.M, Move.L));
            case Y -> applyMoves(cube, List.of(Move.U, Move.E, Move.D_PRIME));
            case Y2 -> applyMoves(cube, List.of(Move.Y, Move.Y));
            case Y_PRIME -> applyMoves(cube, List.of(Move.Y, Move.Y, Move.Y));
            case Z -> applyMoves(cube, List.of(Move.F, Move.S, Move.B_PRIME));
            case Z2 -> applyMoves(cube, List.of(Move.Z, Move.Z));
            case Z_PRIME -> applyMoves(cube, List.of(Move.F_PRIME, Move.S_PRIME, Move.B));
            default -> throw new IllegalArgumentException("Not a cube rotation: " + move);
        }
    }

    private static void applyWideMove(CubeState cube, Move move) {
        switch (move) {
            case UW -> applyMoves(cube, List.of(Move.U, Move.E_PRIME));
            case UW2 -> applyMoves(cube, List.of(Move.U2, Move.E2));
            case UW_PRIME -> applyMoves(cube, List.of(Move.U_PRIME, Move.E));
            case RW -> applyMoves(cube, List.of(Move.R, Move.M_PRIME));
            case RW2 -> applyMoves(cube, List.of(Move.R2, Move.M2));
            case RW_PRIME -> applyMoves(cube, List.of(Move.R_PRIME, Move.M));
            case FW -> applyMoves(cube, List.of(Move.F, Move.S));
            case FW2 -> applyMoves(cube, List.of(Move.F2, Move.S2));
            case FW_PRIME -> applyMoves(cube, List.of(Move.F_PRIME, Move.S_PRIME));
            case DW -> applyMoves(cube, List.of(Move.D, Move.E));
            case DW2 -> applyMoves(cube, List.of(Move.D2, Move.E2));
            case DW_PRIME -> applyMoves(cube, List.of(Move.D_PRIME, Move.E_PRIME));
            case LW -> applyMoves(cube, List.of(Move.L, Move.M));
            case LW2 -> applyMoves(cube, List.of(Move.L2, Move.M2));
            case LW_PRIME -> applyMoves(cube, List.of(Move.L_PRIME, Move.M_PRIME));
            case BW -> applyMoves(cube, List.of(Move.B, Move.S_PRIME));
            case BW2 -> applyMoves(cube, List.of(Move.B2, Move.S2));
            case BW_PRIME -> applyMoves(cube, List.of(Move.B_PRIME, Move.S));
            default -> throw new IllegalArgumentException("Not a wide move: " + move);
        }
    }
}
