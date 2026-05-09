package cfop;

import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import io.CubeConverter;
import io.FaceletState;

public class OLLAnalyzer {
    public static boolean isOllSolved(CubeState cube) {
        return isOllSolved(cube, new CubeOrientation());
    }

    public static boolean isOllSolved(CubeState cube, CubeOrientation orientation) {
        var signature = extractSignature(cube, orientation);
        return signature.u0()
                && signature.u1()
                && signature.u2()
                && signature.u3()
                && signature.u5()
                && signature.u6()
                && signature.u7()
                && signature.u8();
    }

    public static OLLCaseSignature extractSignature(CubeState cube) {
        return extractSignature(cube, new CubeOrientation());
    }

    public static OLLCaseSignature extractSignature(CubeState cube, CubeOrientation orientation) {
        var facelets = CubeConverter.toFaceletStateAllowingCenterParity(cube);

        return new OLLCaseSignature(
                hasLogicalUSticker(facelets, orientation, Face.U, 0),
                hasLogicalUSticker(facelets, orientation, Face.U, 1),
                hasLogicalUSticker(facelets, orientation, Face.U, 2),
                hasLogicalUSticker(facelets, orientation, Face.U, 3),
                hasLogicalUSticker(facelets, orientation, Face.U, 5),
                hasLogicalUSticker(facelets, orientation, Face.U, 6),
                hasLogicalUSticker(facelets, orientation, Face.U, 7),
                hasLogicalUSticker(facelets, orientation, Face.U, 8),
                hasLogicalUSticker(facelets, orientation, Face.F, 0),
                hasLogicalUSticker(facelets, orientation, Face.F, 1),
                hasLogicalUSticker(facelets, orientation, Face.F, 2),
                hasLogicalUSticker(facelets, orientation, Face.R, 0),
                hasLogicalUSticker(facelets, orientation, Face.R, 1),
                hasLogicalUSticker(facelets, orientation, Face.R, 2),
                hasLogicalUSticker(facelets, orientation, Face.B, 0),
                hasLogicalUSticker(facelets, orientation, Face.B, 1),
                hasLogicalUSticker(facelets, orientation, Face.B, 2),
                hasLogicalUSticker(facelets, orientation, Face.L, 0),
                hasLogicalUSticker(facelets, orientation, Face.L, 1),
                hasLogicalUSticker(facelets, orientation, Face.L, 2)
        );
    }

    private static boolean hasLogicalUSticker(FaceletState facelets, CubeOrientation orientation, Face logicalFace, int index) {
        return logicalSticker(facelets, orientation, logicalFace, index) == Face.U;
    }

    private static Face logicalSticker(FaceletState facelets, CubeOrientation orientation, Face logicalFace, int index) {
        var physicalFace = orientation.faceAt(logicalFace);
        var physicalIndex = physicalIndexForLogicalFacelet(orientation, logicalFace, index);
        var physicalSticker = facelets.getSticker(physicalFace, physicalIndex);
        return orientation.logicalFaceOf(physicalSticker);
    }

    private static int physicalIndexForLogicalFacelet(CubeOrientation orientation, Face logicalFace, int index) {
        var logicalPosition = positionFor(logicalFace, index);
        var physicalPosition = transform(orientation, logicalPosition);
        return indexFor(orientation.faceAt(logicalFace), physicalPosition);
    }

    private static Vec transform(CubeOrientation orientation, Vec logicalPosition) {
        return vectorForLogicalAxis(orientation, Face.R).scale(logicalPosition.x())
                .add(vectorForLogicalAxis(orientation, Face.U).scale(logicalPosition.y()))
                .add(vectorForLogicalAxis(orientation, Face.F).scale(logicalPosition.z()));
    }

    private static Vec vectorForLogicalAxis(CubeOrientation orientation, Face positiveLogicalAxis) {
        return vectorForFace(orientation.faceAt(positiveLogicalAxis));
    }

    private static Vec positionFor(Face face, int index) {
        var row = index / 3;
        var col = index % 3;
        var horizontal = col - 1;
        var vertical = row - 1;

        return switch (face) {
            case U -> new Vec(horizontal, 1, vertical);
            case D -> new Vec(horizontal, -1, -vertical);
            case F -> new Vec(horizontal, -vertical, 1);
            case B -> new Vec(-horizontal, -vertical, -1);
            case R -> new Vec(1, -vertical, -horizontal);
            case L -> new Vec(-1, -vertical, horizontal);
        };
    }

    private static int indexFor(Face face, Vec position) {
        var rowCol = switch (face) {
            case U -> new RowCol(position.z() + 1, position.x() + 1);
            case D -> new RowCol(1 - position.z(), position.x() + 1);
            case F -> new RowCol(1 - position.y(), position.x() + 1);
            case B -> new RowCol(1 - position.y(), 1 - position.x());
            case R -> new RowCol(1 - position.y(), 1 - position.z());
            case L -> new RowCol(1 - position.y(), position.z() + 1);
        };
        if (rowCol.row() < 0 || rowCol.row() > 2 || rowCol.col() < 0 || rowCol.col() > 2) {
            throw new IllegalStateException("Facelet position is outside face " + face + ": " + position);
        }
        return rowCol.row() * 3 + rowCol.col();
    }

    private static Vec vectorForFace(Face face) {
        return switch (face) {
            case U -> new Vec(0, 1, 0);
            case R -> new Vec(1, 0, 0);
            case F -> new Vec(0, 0, 1);
            case D -> new Vec(0, -1, 0);
            case L -> new Vec(-1, 0, 0);
            case B -> new Vec(0, 0, -1);
        };
    }

    private record Vec(int x, int y, int z) {
        private Vec add(Vec other) {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        private Vec scale(int factor) {
            return new Vec(x * factor, y * factor, z * factor);
        }
    }

    private record RowCol(int row, int col) {
    }
}
