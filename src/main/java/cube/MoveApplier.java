package cube;

import java.util.List;

public class MoveApplier {
    public static void applyMove(CubeState cube, Move move) {
        if (isCubeRotation(move)) {
            applyCubeRotation(cube, move);
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
}
