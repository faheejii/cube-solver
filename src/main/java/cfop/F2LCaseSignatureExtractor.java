package cfop;

import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.MoveApplier;

import java.util.Arrays;

public final class F2LCaseSignatureExtractor {
    private F2LCaseSignatureExtractor() {
    }

    public static F2LCaseSignature extract(CubeState cube, F2LSlot slot) {
        var rotation = F2LCaseFrame.rotationToCanonical(slot);
        var normalized = copyCube(cube);
        MoveApplier.applyMoves(normalized, rotation.getMoves());

        var targetPair = canonicalTargetPair(rotation);

        var cornerPosition = findCornerPosition(normalized, targetPair.corner());
        var edgePosition = findEdgePosition(normalized, targetPair.edge());
        var cornerOrientation = normalized.cornerOri[cornerPosition.ordinal()];
        var edgeOrientation = normalized.edgeOri[edgePosition.ordinal()];

        return new F2LCaseSignature(
                cornerPosition,
                cornerOrientation,
                edgePosition,
                edgeOrientation
        );
    }

    public static F2LCaseSignature extract(CubeState cube, Corner targetCorner, Edge targetEdge, CubeOrientation orientation) {
        var rawCornerPosition = findCornerPosition(cube, targetCorner);
        var rawEdgePosition = findEdgePosition(cube, targetEdge);

        return new F2LCaseSignature(
                mapCornerPosition(rawCornerPosition, orientation),
                cube.cornerOri[rawCornerPosition.ordinal()],
                mapEdgePosition(rawEdgePosition, orientation),
                cube.edgeOri[rawEdgePosition.ordinal()]
        );
    }

    private static TargetPair canonicalTargetPair(cube.Algorithm rotation) {
        var solved = new CubeState();
        MoveApplier.applyMoves(solved, rotation.getMoves());
        return new TargetPair(
                Corner.values()[solved.cornerPerm[Corner.DFR.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.FR.ordinal()]]
        );
    }

    private static Corner findCornerPosition(CubeState cube, Corner targetCorner) {
        for (var position : Corner.values()) {
            if (cube.cornerPerm[position.ordinal()] == targetCorner.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing target corner: " + targetCorner);
    }

    private static Edge findEdgePosition(CubeState cube, Edge targetEdge) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == targetEdge.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing target edge: " + targetEdge);
    }

    private static CubeState copyCube(CubeState cube) {
        var copy = new CubeState();
        copy.cornerPerm = Arrays.copyOf(cube.cornerPerm, cube.cornerPerm.length);
        copy.cornerOri = Arrays.copyOf(cube.cornerOri, cube.cornerOri.length);
        copy.edgePerm = Arrays.copyOf(cube.edgePerm, cube.edgePerm.length);
        copy.edgeOri = Arrays.copyOf(cube.edgeOri, cube.edgeOri.length);
        return copy;
    }

    private static Edge mapEdgePosition(Edge rawPosition, CubeOrientation orientation) {
        var faces = edgeFaces(rawPosition);
        return edgeForFaces(
                orientation.logicalFaceOf(faces[0]),
                orientation.logicalFaceOf(faces[1])
        );
    }

    private static Corner mapCornerPosition(Corner rawPosition, CubeOrientation orientation) {
        var faces = cornerFaces(rawPosition);
        return cornerForFaces(
                orientation.logicalFaceOf(faces[0]),
                orientation.logicalFaceOf(faces[1]),
                orientation.logicalFaceOf(faces[2])
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

    private record TargetPair(Corner corner, Edge edge) {
    }
}
