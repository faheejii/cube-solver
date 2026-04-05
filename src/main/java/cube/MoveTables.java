package cube;

public final class MoveTables {
    public static final byte[][] CORNER_PERM_MOVE = new byte[Move.values().length][8];
    public static final byte[][] CORNER_ORI_DELTA = new byte[Move.values().length][8];
    public static final byte[][] EDGE_PERM_MOVE = new byte[Move.values().length][12];
    public static final byte[][] EDGE_ORI_DELTA = new byte[Move.values().length][12];

    static {
        initializeIdentities();
        initializeQuarterTurns();
        initializeSliceTurns();
        deriveRepeatedTurns(Move.U, Move.U2, Move.U_PRIME);
        deriveRepeatedTurns(Move.R, Move.R2, Move.R_PRIME);
        deriveRepeatedTurns(Move.F, Move.F2, Move.F_PRIME);
        deriveRepeatedTurns(Move.D, Move.D2, Move.D_PRIME);
        deriveRepeatedTurns(Move.L, Move.L2, Move.L_PRIME);
        deriveRepeatedTurns(Move.B, Move.B2, Move.B_PRIME);
        deriveRepeatedTurns(Move.M, Move.M2, Move.M_PRIME);
        deriveRepeatedTurns(Move.E, Move.E2, Move.E_PRIME);
        deriveRepeatedTurns(Move.S, Move.S2, Move.S_PRIME);
    }

    private MoveTables() {
    }

    private static void initializeIdentities() {
        for (var move : Move.values()) {
            for (byte i = 0; i < CORNER_PERM_MOVE[move.ordinal()].length; i++) {
                CORNER_PERM_MOVE[move.ordinal()][i] = i;
                CORNER_ORI_DELTA[move.ordinal()][i] = 0;
            }

            for (byte i = 0; i < EDGE_PERM_MOVE[move.ordinal()].length; i++) {
                EDGE_PERM_MOVE[move.ordinal()][i] = i;
                EDGE_ORI_DELTA[move.ordinal()][i] = 0;
            }
        }
    }

    private static void initializeQuarterTurns() {
        setCornerMove(
                Move.U,
                Corner.UBR, Corner.URF, Corner.UFL, Corner.ULB,
                Corner.DFR, Corner.DLF, Corner.DBL, Corner.DRB,
                0, 0, 0, 0, 0, 0, 0, 0
        );
        setEdgeMove(
                Move.U,
                Edge.UB, Edge.UR, Edge.UF, Edge.UL,
                Edge.DR, Edge.DF, Edge.DL, Edge.DB,
                Edge.FR, Edge.FL, Edge.BL, Edge.BR,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );

        setCornerMove(
                Move.R,
                Corner.DFR, Corner.UFL, Corner.ULB, Corner.URF,
                Corner.DRB, Corner.DLF, Corner.DBL, Corner.UBR,
                2, 0, 0, 1, 1, 0, 0, 2
        );
        setEdgeMove(
                Move.R,
                Edge.FR, Edge.UF, Edge.UL, Edge.UB,
                Edge.BR, Edge.DF, Edge.DL, Edge.DB,
                Edge.DR, Edge.FL, Edge.BL, Edge.UR,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );

        setCornerMove(
                Move.F,
                Corner.UFL, Corner.DLF, Corner.ULB, Corner.UBR,
                Corner.URF, Corner.DFR, Corner.DBL, Corner.DRB,
                1, 2, 0, 0, 2, 1, 0, 0
        );
        setEdgeMove(
                Move.F,
                Edge.UR, Edge.FL, Edge.UL, Edge.UB,
                Edge.DR, Edge.FR, Edge.DL, Edge.DB,
                Edge.UF, Edge.DF, Edge.BL, Edge.BR,
                0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0
        );

        setCornerMove(
                Move.D,
                Corner.URF, Corner.UFL, Corner.ULB, Corner.UBR,
                Corner.DLF, Corner.DBL, Corner.DRB, Corner.DFR,
                0, 0, 0, 0, 0, 0, 0, 0
        );
        setEdgeMove(
                Move.D,
                Edge.UR, Edge.UF, Edge.UL, Edge.UB,
                Edge.DF, Edge.DL, Edge.DB, Edge.DR,
                Edge.FR, Edge.FL, Edge.BL, Edge.BR,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );

        setCornerMove(
                Move.L,
                Corner.URF, Corner.ULB, Corner.DBL, Corner.UBR,
                Corner.DFR, Corner.UFL, Corner.DLF, Corner.DRB,
                0, 1, 2, 0, 0, 2, 1, 0
        );
        setEdgeMove(
                Move.L,
                Edge.UR, Edge.UF, Edge.BL, Edge.UB,
                Edge.DR, Edge.DF, Edge.FL, Edge.DB,
                Edge.FR, Edge.UL, Edge.DL, Edge.BR,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );

        setCornerMove(
                Move.B,
                Corner.URF, Corner.UFL, Corner.UBR, Corner.DRB,
                Corner.DFR, Corner.DLF, Corner.ULB, Corner.DBL,
                0, 0, 1, 2, 0, 0, 2, 1
        );
        setEdgeMove(
                Move.B,
                Edge.UR, Edge.UF, Edge.UL, Edge.BR,
                Edge.DR, Edge.DF, Edge.DL, Edge.BL,
                Edge.FR, Edge.FL, Edge.UB, Edge.DB,
                0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1
        );
    }

    private static void initializeSliceTurns() {
        setEdgeMove(
                Move.M,
                Edge.UR, Edge.UB, Edge.UL, Edge.DB,
                Edge.DR, Edge.UF, Edge.DL, Edge.DF,
                Edge.FR, Edge.FL, Edge.BL, Edge.BR,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );
        setEdgeMove(
                Move.E,
                Edge.UR, Edge.UF, Edge.UL, Edge.UB,
                Edge.DR, Edge.DF, Edge.DL, Edge.DB,
                Edge.BR, Edge.FR, Edge.FL, Edge.BL,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );
        setEdgeMove(
                Move.S,
                Edge.UL, Edge.UF, Edge.DL, Edge.UB,
                Edge.UR, Edge.DF, Edge.DR, Edge.DB,
                Edge.FR, Edge.FL, Edge.BL, Edge.BR,
                1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0
        );
    }

    private static void deriveRepeatedTurns(Move quarterTurn, Move halfTurn, Move primeTurn) {
        compose(halfTurn, quarterTurn, quarterTurn);
        compose(primeTurn, halfTurn, quarterTurn);
    }

    private static void compose(Move result, Move first, Move second) {
        var resultIndex = result.ordinal();
        var firstIndex = first.ordinal();
        var secondIndex = second.ordinal();

        for (int i = 0; i < CORNER_PERM_MOVE[resultIndex].length; i++) {
            var through = CORNER_PERM_MOVE[secondIndex][i];
            CORNER_PERM_MOVE[resultIndex][i] = CORNER_PERM_MOVE[firstIndex][through];
            CORNER_ORI_DELTA[resultIndex][i] = (byte) (
                    (CORNER_ORI_DELTA[firstIndex][through] + CORNER_ORI_DELTA[secondIndex][i]) % 3
            );
        }

        for (int i = 0; i < EDGE_PERM_MOVE[resultIndex].length; i++) {
            var through = EDGE_PERM_MOVE[secondIndex][i];
            EDGE_PERM_MOVE[resultIndex][i] = EDGE_PERM_MOVE[firstIndex][through];
            EDGE_ORI_DELTA[resultIndex][i] = (byte) (
                    (EDGE_ORI_DELTA[firstIndex][through] + EDGE_ORI_DELTA[secondIndex][i]) % 2
            );
        }
    }

    private static void setCornerMove(
            Move move,
            Corner c0, Corner c1, Corner c2, Corner c3,
            Corner c4, Corner c5, Corner c6, Corner c7,
            int o0, int o1, int o2, int o3, int o4, int o5, int o6, int o7
    ) {
        var moveIndex = move.ordinal();
        CORNER_PERM_MOVE[moveIndex] = new byte[]{
                (byte) c0.ordinal(), (byte) c1.ordinal(), (byte) c2.ordinal(), (byte) c3.ordinal(),
                (byte) c4.ordinal(), (byte) c5.ordinal(), (byte) c6.ordinal(), (byte) c7.ordinal()
        };
        CORNER_ORI_DELTA[moveIndex] = new byte[]{
                (byte) o0, (byte) o1, (byte) o2, (byte) o3,
                (byte) o4, (byte) o5, (byte) o6, (byte) o7
        };
    }

    private static void setEdgeMove(
            Move move,
            Edge e0, Edge e1, Edge e2, Edge e3,
            Edge e4, Edge e5, Edge e6, Edge e7,
            Edge e8, Edge e9, Edge e10, Edge e11,
            int o0, int o1, int o2, int o3, int o4, int o5, int o6, int o7, int o8, int o9, int o10, int o11
    ) {
        var moveIndex = move.ordinal();
        EDGE_PERM_MOVE[moveIndex] = new byte[]{
                (byte) e0.ordinal(), (byte) e1.ordinal(), (byte) e2.ordinal(), (byte) e3.ordinal(),
                (byte) e4.ordinal(), (byte) e5.ordinal(), (byte) e6.ordinal(), (byte) e7.ordinal(),
                (byte) e8.ordinal(), (byte) e9.ordinal(), (byte) e10.ordinal(), (byte) e11.ordinal()
        };
        EDGE_ORI_DELTA[moveIndex] = new byte[]{
                (byte) o0, (byte) o1, (byte) o2, (byte) o3,
                (byte) o4, (byte) o5, (byte) o6, (byte) o7,
                (byte) o8, (byte) o9, (byte) o10, (byte) o11
        };
    }
}
