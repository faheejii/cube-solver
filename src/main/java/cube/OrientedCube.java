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

    private static boolean isCubeRotation(Move move) {
        return switch (move) {
            case X, X2, X_PRIME, Y, Y2, Y_PRIME, Z, Z2, Z_PRIME -> true;
            default -> false;
        };
    }
}
