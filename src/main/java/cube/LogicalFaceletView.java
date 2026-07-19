package cube;

import io.CubeConverter;
import io.FaceletState;

public final class LogicalFaceletView {
    private final CubeOrientation orientation;
    private final FaceletState physical;

    private LogicalFaceletView(CubeState cube, CubeOrientation orientation) {
        this.orientation = orientation.copy();
        this.physical = CubeConverter.toFaceletStateAllowingCenterParity(cube);
    }

    public static LogicalFaceletView of(CubeState cube, CubeOrientation orientation) {
        if (cube == null || orientation == null) {
            throw new IllegalArgumentException("cube and orientation cannot be null");
        }
        return new LogicalFaceletView(cube, orientation);
    }

    public Face sticker(Face logicalFace, int index) {
        var physicalFace = orientation.faceAt(logicalFace);
        var physicalIndex = physicalIndexForLogicalFacelet(logicalFace, index);
        return orientation.logicalFaceOf(physical.getSticker(physicalFace, physicalIndex));
    }

    private int physicalIndexForLogicalFacelet(Face logicalFace, int index) {
        var logicalPosition = positionFor(logicalFace, index);
        var physicalPosition = vectorFor(orientation.faceAt(Face.R)).scale(logicalPosition.x())
                .add(vectorFor(orientation.faceAt(Face.U)).scale(logicalPosition.y()))
                .add(vectorFor(orientation.faceAt(Face.F)).scale(logicalPosition.z()));
        return indexFor(orientation.faceAt(logicalFace), physicalPosition);
    }

    private static Vector vectorFor(Face face) {
        return switch (face) {
            case U -> new Vector(0, 1, 0);
            case R -> new Vector(1, 0, 0);
            case F -> new Vector(0, 0, 1);
            case D -> new Vector(0, -1, 0);
            case L -> new Vector(-1, 0, 0);
            case B -> new Vector(0, 0, -1);
        };
    }

    private static Vector positionFor(Face face, int index) {
        if (index < 0 || index >= FaceletState.FACELET_COUNT_PER_FACE) {
            throw new IndexOutOfBoundsException("Facelet index must be between 0 and 8");
        }
        var row = index / 3;
        var col = index % 3;
        var horizontal = col - 1;
        var vertical = row - 1;
        return switch (face) {
            case U -> new Vector(horizontal, 1, vertical);
            case D -> new Vector(horizontal, -1, -vertical);
            case F -> new Vector(horizontal, -vertical, 1);
            case B -> new Vector(-horizontal, -vertical, -1);
            case R -> new Vector(1, -vertical, -horizontal);
            case L -> new Vector(-1, -vertical, horizontal);
        };
    }

    private static int indexFor(Face face, Vector position) {
        var rowCol = switch (face) {
            case U -> new int[]{position.z() + 1, position.x() + 1};
            case D -> new int[]{1 - position.z(), position.x() + 1};
            case F -> new int[]{1 - position.y(), position.x() + 1};
            case B -> new int[]{1 - position.y(), 1 - position.x()};
            case R -> new int[]{1 - position.y(), 1 - position.z()};
            case L -> new int[]{1 - position.y(), position.z() + 1};
        };
        if (rowCol[0] < 0 || rowCol[0] > 2 || rowCol[1] < 0 || rowCol[1] > 2) {
            throw new IllegalStateException("Facelet position is outside face " + face + ": " + position);
        }
        return rowCol[0] * 3 + rowCol[1];
    }

    private record Vector(int x, int y, int z) {
        private Vector add(Vector other) {
            return new Vector(x + other.x(), y + other.y(), z + other.z());
        }

        private Vector scale(int factor) {
            return new Vector(x * factor, y * factor, z * factor);
        }
    }
}
