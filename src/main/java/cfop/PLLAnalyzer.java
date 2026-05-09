package cfop;

import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;

public class PLLAnalyzer {
    public static boolean isPllSolved(CubeState cube) {
        return isPllSolved(cube, new CubeOrientation());
    }

    public static boolean isPllSolved(CubeState cube, CubeOrientation orientation) {
        return extractSignature(cube, orientation).equals(solvedSignature());
    }

    public static PLLCaseSignature extractSignature(CubeState cube) {
        return extractSignature(cube, new CubeOrientation());
    }

    public static PLLCaseSignature extractSignature(CubeState cube, CubeOrientation orientation) {
        return new PLLCaseSignature(
                logicalCornerPieceAt(cube, Corner.URF, orientation),
                logicalCornerPieceAt(cube, Corner.UFL, orientation),
                logicalCornerPieceAt(cube, Corner.ULB, orientation),
                logicalCornerPieceAt(cube, Corner.UBR, orientation),
                logicalEdgePieceAt(cube, Edge.UR, orientation),
                logicalEdgePieceAt(cube, Edge.UF, orientation),
                logicalEdgePieceAt(cube, Edge.UL, orientation),
                logicalEdgePieceAt(cube, Edge.UB, orientation)
        );
    }

    private static PLLCaseSignature solvedSignature() {
        return new PLLCaseSignature(
                Corner.URF,
                Corner.UFL,
                Corner.ULB,
                Corner.UBR,
                Edge.UR,
                Edge.UF,
                Edge.UL,
                Edge.UB
        );
    }

    private static Corner logicalCornerPieceAt(CubeState cube, Corner logicalPosition, CubeOrientation orientation) {
        var physicalPosition = physicalCornerForLogicalPosition(logicalPosition, orientation);
        var physicalPiece = Corner.values()[cube.cornerPerm[physicalPosition.ordinal()]];
        return logicalCornerForPhysicalCorner(physicalPiece, orientation);
    }

    private static Edge logicalEdgePieceAt(CubeState cube, Edge logicalPosition, CubeOrientation orientation) {
        var physicalPosition = physicalEdgeForLogicalPosition(logicalPosition, orientation);
        var physicalPiece = Edge.values()[cube.edgePerm[physicalPosition.ordinal()]];
        return logicalEdgeForPhysicalEdge(physicalPiece, orientation);
    }

    private static Corner physicalCornerForLogicalPosition(Corner logicalPosition, CubeOrientation orientation) {
        var faces = cornerFaces(logicalPosition);
        return cornerForFaces(
                orientation.faceAt(faces[0]),
                orientation.faceAt(faces[1]),
                orientation.faceAt(faces[2])
        );
    }

    private static Edge physicalEdgeForLogicalPosition(Edge logicalPosition, CubeOrientation orientation) {
        var faces = edgeFaces(logicalPosition);
        return edgeForFaces(
                orientation.faceAt(faces[0]),
                orientation.faceAt(faces[1])
        );
    }

    private static Corner logicalCornerForPhysicalCorner(Corner physicalCorner, CubeOrientation orientation) {
        var faces = cornerFaces(physicalCorner);
        return cornerForFaces(
                orientation.logicalFaceOf(faces[0]),
                orientation.logicalFaceOf(faces[1]),
                orientation.logicalFaceOf(faces[2])
        );
    }

    private static Edge logicalEdgeForPhysicalEdge(Edge physicalEdge, CubeOrientation orientation) {
        var faces = edgeFaces(physicalEdge);
        return edgeForFaces(
                orientation.logicalFaceOf(faces[0]),
                orientation.logicalFaceOf(faces[1])
        );
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
