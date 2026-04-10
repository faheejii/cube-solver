package cube;

import java.util.List;

public class OrientedCube {
    private final CubeState cube;
    private final CubeOrientation orientation;

    public OrientedCube() {
        this(new CubeState(), new CubeOrientation());
    }

    public OrientedCube(CubeState cube) {
        this(cube, new CubeOrientation());
    }

    public OrientedCube(CubeState cube, CubeOrientation orientation) {
        this.cube = cube;
        this.orientation = orientation.copy();
    }

    public void applyMove(Move move) {
        if (isCubeRotation(move)) {
            orientation.applyRotation(move);
            return;
        }
        if (isWideMove(move)) {
            applyWideMove(move);
            return;
        }

        MoveApplier.applyMove(cube, orientation.mapMove(move));
    }

    public void applyMoves(List<Move> moves) {
        for (var move : moves) {
            applyMove(move);
        }
    }

    public void applyAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return;
        }
        applyMoves(Algorithm.parse(algorithm).getMoves());
    }

    public Face faceAt(Face logicalFace) {
        return orientation.faceAt(logicalFace);
    }

    public CubeState cubeState() {
        return cube;
    }

    public CubeOrientation orientation() {
        return orientation.copy();
    }

    public void collapseOrientationIntoCube() {
        if (isIdentityOrientation()) {
            return;
        }

        var newCornerPerm = new byte[8];
        var newCornerOri = new byte[8];
        for (var logicalPosition : Corner.values()) {
            var physicalPosition = cornerForFaces(
                    orientation.faceAt(cornerFaces(logicalPosition)[0]),
                    orientation.faceAt(cornerFaces(logicalPosition)[1]),
                    orientation.faceAt(cornerFaces(logicalPosition)[2])
            );
            newCornerPerm[logicalPosition.ordinal()] = cube.cornerPerm[physicalPosition.ordinal()];
            newCornerOri[logicalPosition.ordinal()] = cube.cornerOri[physicalPosition.ordinal()];
        }

        var newEdgePerm = new byte[12];
        var newEdgeOri = new byte[12];
        for (var logicalPosition : Edge.values()) {
            var physicalPosition = edgeForFaces(
                    orientation.faceAt(edgeFaces(logicalPosition)[0]),
                    orientation.faceAt(edgeFaces(logicalPosition)[1])
            );
            newEdgePerm[logicalPosition.ordinal()] = cube.edgePerm[physicalPosition.ordinal()];
            newEdgeOri[logicalPosition.ordinal()] = cube.edgeOri[physicalPosition.ordinal()];
        }

        cube.cornerPerm = newCornerPerm;
        cube.cornerOri = newCornerOri;
        cube.edgePerm = newEdgePerm;
        cube.edgeOri = newEdgeOri;
        orientation.reset();
    }

    private static boolean isCubeRotation(Move move) {
        return switch (move) {
            case X, X2, X_PRIME, Y, Y2, Y_PRIME, Z, Z2, Z_PRIME -> true;
            default -> false;
        };
    }

    private void applyWideMove(Move move) {
        switch (move) {
            case RW -> {
                applyMove(Move.L);
                applyMove(Move.X);
            }
            case RW2 -> {
                applyMove(Move.L2);
                applyMove(Move.X2);
            }
            case RW_PRIME -> {
                applyMove(Move.L_PRIME);
                applyMove(Move.X_PRIME);
            }
            case LW -> {
                applyMove(Move.R);
                applyMove(Move.X_PRIME);
            }
            case LW2 -> {
                applyMove(Move.R2);
                applyMove(Move.X2);
            }
            case LW_PRIME -> {
                applyMove(Move.R_PRIME);
                applyMove(Move.X);
            }
            case UW -> {
                applyMove(Move.D);
                applyMove(Move.Y);
            }
            case UW2 -> {
                applyMove(Move.D2);
                applyMove(Move.Y2);
            }
            case UW_PRIME -> {
                applyMove(Move.D_PRIME);
                applyMove(Move.Y_PRIME);
            }
            case DW -> {
                applyMove(Move.U);
                applyMove(Move.Y_PRIME);
            }
            case DW2 -> {
                applyMove(Move.U2);
                applyMove(Move.Y2);
            }
            case DW_PRIME -> {
                applyMove(Move.U_PRIME);
                applyMove(Move.Y);
            }
            case FW -> {
                applyMove(Move.B);
                applyMove(Move.Z);
            }
            case FW2 -> {
                applyMove(Move.B2);
                applyMove(Move.Z2);
            }
            case FW_PRIME -> {
                applyMove(Move.B_PRIME);
                applyMove(Move.Z_PRIME);
            }
            case BW -> {
                applyMove(Move.F);
                applyMove(Move.Z_PRIME);
            }
            case BW2 -> {
                applyMove(Move.F2);
                applyMove(Move.Z2);
            }
            case BW_PRIME -> {
                applyMove(Move.F_PRIME);
                applyMove(Move.Z);
            }
            default -> throw new IllegalArgumentException("Not a wide move: " + move);
        }
    }

    private boolean isIdentityOrientation() {
        return orientation.faceAt(Face.U) == Face.U
                && orientation.faceAt(Face.R) == Face.R
                && orientation.faceAt(Face.F) == Face.F;
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

    private static Face[] edgeFaces(Edge edge) {
        return switch (edge) {
            case UR -> new Face[]{Face.U, Face.R};
            case UF -> new Face[]{Face.U, Face.F};
            case UL -> new Face[]{Face.U, Face.L};
            case UB -> new Face[]{Face.U, Face.B};
            case DR -> new Face[]{Face.D, Face.R};
            case DF -> new Face[]{Face.D, Face.F};
            case DL -> new Face[]{Face.D, Face.L};
            case DB -> new Face[]{Face.D, Face.B};
            case FR -> new Face[]{Face.F, Face.R};
            case FL -> new Face[]{Face.F, Face.L};
            case BL -> new Face[]{Face.B, Face.L};
            case BR -> new Face[]{Face.B, Face.R};
        };
    }

    private static Face[] cornerFaces(Corner corner) {
        return switch (corner) {
            case URF -> new Face[]{Face.U, Face.R, Face.F};
            case UFL -> new Face[]{Face.U, Face.F, Face.L};
            case ULB -> new Face[]{Face.U, Face.L, Face.B};
            case UBR -> new Face[]{Face.U, Face.B, Face.R};
            case DFR -> new Face[]{Face.D, Face.F, Face.R};
            case DLF -> new Face[]{Face.D, Face.L, Face.F};
            case DBL -> new Face[]{Face.D, Face.B, Face.L};
            case DRB -> new Face[]{Face.D, Face.R, Face.B};
        };
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
}
