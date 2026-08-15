package cfop;

import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import io.CubeConverter;
import io.FaceletState;

public final class F2LCaseSignatureExtractor {
    private static final StickerRef[][] CORNER_FACELETS = {
            {sticker(Face.U, 8), sticker(Face.R, 0), sticker(Face.F, 2)},
            {sticker(Face.U, 6), sticker(Face.F, 0), sticker(Face.L, 2)},
            {sticker(Face.U, 0), sticker(Face.L, 0), sticker(Face.B, 2)},
            {sticker(Face.U, 2), sticker(Face.B, 0), sticker(Face.R, 2)},
            {sticker(Face.D, 2), sticker(Face.F, 8), sticker(Face.R, 6)},
            {sticker(Face.D, 0), sticker(Face.L, 8), sticker(Face.F, 6)},
            {sticker(Face.D, 6), sticker(Face.B, 8), sticker(Face.L, 6)},
            {sticker(Face.D, 8), sticker(Face.R, 8), sticker(Face.B, 6)}
    };
    private static final StickerRef[][] EDGE_FACELETS = {
            {sticker(Face.U, 5), sticker(Face.R, 1)},
            {sticker(Face.U, 7), sticker(Face.F, 1)},
            {sticker(Face.U, 3), sticker(Face.L, 1)},
            {sticker(Face.U, 1), sticker(Face.B, 1)},
            {sticker(Face.D, 5), sticker(Face.R, 7)},
            {sticker(Face.D, 1), sticker(Face.F, 7)},
            {sticker(Face.D, 3), sticker(Face.L, 7)},
            {sticker(Face.D, 7), sticker(Face.B, 7)},
            {sticker(Face.F, 5), sticker(Face.R, 3)},
            {sticker(Face.F, 3), sticker(Face.L, 5)},
            {sticker(Face.B, 5), sticker(Face.L, 3)},
            {sticker(Face.B, 3), sticker(Face.R, 5)}
    };
    private F2LCaseSignatureExtractor() {
    }

    public static F2LCaseSignature extract(CubeState cube, F2LSlot slot) {
        return extract(cube, slot, new CubeOrientation());
    }

    public static F2LCaseSignature extract(CubeState cube, F2LSlot slot, CubeOrientation orientation) {
        var targetPair = targetPair(slot, orientation);
        return extract(
                cube,
                targetPair.corner(),
                targetPair.edge(),
                orientation
        );
    }

    public static F2LCaseSignature extract(CubeState cube, Corner targetCorner, Edge targetEdge, CubeOrientation orientation) {
        var rawCornerPosition = findCornerPosition(cube, targetCorner);
        var rawEdgePosition = findEdgePosition(cube, targetEdge);

        return new F2LCaseSignature(
                mapCornerPosition(rawCornerPosition, orientation),
                cornerOrientation(cube, rawCornerPosition, targetCorner, orientation),
                mapEdgePosition(rawEdgePosition, orientation),
                edgeOrientation(cube, rawEdgePosition, targetEdge, orientation)
        );
    }

    private static TargetPair targetPair(F2LSlot slot, CubeOrientation orientation) {
        return switch (slot) {
            case FR -> new TargetPair(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.R))
            );
            case FL -> new TargetPair(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.L))
            );
            case BL -> new TargetPair(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.L))
            );
            case BR -> new TargetPair(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.R))
            );
        };
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

    private static int edgeOrientation(CubeState cube, Edge rawPosition, Edge targetEdge, CubeOrientation orientation) {
        FaceletState facelets = CubeConverter.toFaceletStateAllowingCenterParity(cube);
        var logicalPositionFaces = edgeFaces(mapEdgePosition(rawPosition, orientation));
        var logicalTargetFirstColor = orientation.logicalFaceOf(edgeFaces(targetEdge)[0]);
        var refs = EDGE_FACELETS[rawPosition.ordinal()];
        for (var ref : refs) {
            var logicalColor = orientation.logicalFaceOf(facelets.getSticker(ref.face(), ref.index()));
            if (logicalColor == logicalTargetFirstColor) {
                return orientation.logicalFaceOf(ref.face()) == logicalPositionFaces[0] ? 0 : 1;
            }
        }
        throw new IllegalStateException("Edge is missing its target sticker: " + rawPosition);
    }

    /** Corner orientation must be expressed in the current logical frame. */
    private static int cornerOrientation(
            CubeState cube,
            Corner rawPosition,
            Corner targetCorner,
            CubeOrientation orientation
    ) {
        FaceletState facelets = CubeConverter.toFaceletStateAllowingCenterParity(cube);
        var refs = CORNER_FACELETS[rawPosition.ordinal()];
        var logicalPositionFaces = cornerFaces(mapCornerPosition(rawPosition, orientation));
        var logicalTargetUpDownColor = orientation.logicalFaceOf(cornerFaces(targetCorner)[0]);
        for (int index = 0; index < refs.length; index++) {
            var ref = refs[index];
            var color = facelets.getSticker(ref.face(), ref.index());
            var logicalColor = orientation.logicalFaceOf(color);
            if (logicalColor == logicalTargetUpDownColor) {
                var logicalPositionFace = orientation.logicalFaceOf(ref.face());
                for (int orientationIndex = 0; orientationIndex < logicalPositionFaces.length; orientationIndex++) {
                    if (logicalPositionFaces[orientationIndex] == logicalPositionFace) return orientationIndex;
                }
            }
        }
        throw new IllegalStateException("Corner is missing a logical U/D sticker: " + rawPosition);
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

    private record StickerRef(Face face, int index) {
    }

    private static StickerRef sticker(Face face, int index) {
        return new StickerRef(face, index);
    }

}
