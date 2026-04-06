package cube;

import java.util.List;

public final class OrientationFrames {
    private OrientationFrames() {
    }

    public static Algorithm orientationToD(Face face) {
        return switch (face) {
            case D -> new Algorithm();
            case U -> Algorithm.fromMoves(List.of(Move.Z2));
            case R -> Algorithm.fromMoves(List.of(Move.Z));
            case L -> Algorithm.fromMoves(List.of(Move.Z_PRIME));
            case F -> Algorithm.fromMoves(List.of(Move.X_PRIME));
            case B -> Algorithm.fromMoves(List.of(Move.X));
        };
    }

    public static CubeOrientation orientedFrameFor(Face face) {
        var cube = new OrientedCube();
        cube.applyMoves(orientationToD(face).getMoves());
        return cube.orientation();
    }
}
