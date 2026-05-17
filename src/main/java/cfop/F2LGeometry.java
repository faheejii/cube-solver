package cfop;

import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import io.CubeConverter;
import io.FaceletState;

public final class F2LGeometry {
    private static final F2LSlot[] SLOT_ORDER = {F2LSlot.FR, F2LSlot.FL, F2LSlot.BL, F2LSlot.BR};
    private static final StickerRef[][] CORNER_FACELETS = {
            {sticker(Face.U, 8), sticker(Face.R, 0), sticker(Face.F, 2)}, // URF
            {sticker(Face.U, 6), sticker(Face.F, 0), sticker(Face.L, 2)}, // UFL
            {sticker(Face.U, 0), sticker(Face.L, 0), sticker(Face.B, 2)}, // ULB
            {sticker(Face.U, 2), sticker(Face.B, 0), sticker(Face.R, 2)}, // UBR
            {sticker(Face.D, 2), sticker(Face.F, 8), sticker(Face.R, 6)}, // DFR
            {sticker(Face.D, 0), sticker(Face.L, 8), sticker(Face.F, 6)}, // DLF
            {sticker(Face.D, 6), sticker(Face.B, 8), sticker(Face.L, 6)}, // DBL
            {sticker(Face.D, 8), sticker(Face.R, 8), sticker(Face.B, 6)}  // DRB
    };
    private static final StickerRef[][] EDGE_FACELETS = {
            {sticker(Face.U, 5), sticker(Face.R, 1)}, // UR
            {sticker(Face.U, 7), sticker(Face.F, 1)}, // UF
            {sticker(Face.U, 3), sticker(Face.L, 1)}, // UL
            {sticker(Face.U, 1), sticker(Face.B, 1)}, // UB
            {sticker(Face.D, 5), sticker(Face.R, 7)}, // DR
            {sticker(Face.D, 1), sticker(Face.F, 7)}, // DF
            {sticker(Face.D, 3), sticker(Face.L, 7)}, // DL
            {sticker(Face.D, 7), sticker(Face.B, 7)}, // DB
            {sticker(Face.F, 5), sticker(Face.R, 3)}, // FR
            {sticker(Face.F, 3), sticker(Face.L, 5)}, // FL
            {sticker(Face.B, 5), sticker(Face.L, 3)}, // BL
            {sticker(Face.B, 3), sticker(Face.R, 5)}  // BR
    };

    private F2LGeometry() {
    }

    public static TargetSlot[] targetSlotsForOrientation(CubeOrientation orientation) {
        return new TargetSlot[]{
                targetSlotFor(F2LSlot.FR, orientation),
                targetSlotFor(F2LSlot.FL, orientation),
                targetSlotFor(F2LSlot.BL, orientation),
                targetSlotFor(F2LSlot.BR, orientation)
        };
    }

    public static TargetSlot targetSlotFor(F2LSlot slot, CubeOrientation orientation) {
        return switch (slot) {
            case FR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.R))
            );
            case FL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.L))
            );
            case BL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.L))
            );
            case BR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.R))
            );
        };
    }

    public static Edge[] targetCrossForOrientation(CubeOrientation orientation) {
        return new Edge[]{
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.R)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.L))
        };
    }

    public static SlotPair[] slotPairsForTargets(TargetSlot[] targets) {
        return new SlotPair[]{
                new SlotPair(targets[0].corner(), targets[0].edge()),
                new SlotPair(targets[1].corner(), targets[1].edge()),
                new SlotPair(targets[2].corner(), targets[2].edge()),
                new SlotPair(targets[3].corner(), targets[3].edge())
        };
    }

    public static boolean isPairSolved(CubeState cube, SlotPair pair, SlotPair[] stagePairs, CubeOrientation orientation) {
        var targetSlot = targetSlotForPair(pair, stagePairs, targetSlotsForOrientation(orientation), orientation);
        return isTargetSlotSolved(cube, targetSlot);
    }

    public static boolean isPairSolved(
            CubeState cube,
            SlotPair pair,
            SlotPair[] stagePairs,
            TargetSlot[] stageTargets,
            CubeOrientation orientation
    ) {
        var targetSlot = targetSlotForPair(pair, stagePairs, stageTargets, orientation);
        return isTargetSlotSolved(cube, targetSlot);
    }

    public static TargetSlot targetSlotForPair(
            SlotPair pair,
            SlotPair[] stagePairs,
            TargetSlot[] stageTargets,
            CubeOrientation orientation
    ) {
        var index = slotIndexForPair(pair, stagePairs);
        var visibleSlot = visibleSlotForStageIndex(index, stageTargets, orientation);
        return targetSlotFor(visibleSlot, orientation);
    }

    public static F2LSlot visibleSlotForStageIndex(int index, TargetSlot[] stageTargets, CubeOrientation orientation) {
        var originalTarget = stageTargets[index];
        for (var slot : SLOT_ORDER) {
            var visibleTarget = targetSlotFor(slot, orientation);
            if (visibleTarget.corner() == originalTarget.corner() && visibleTarget.edge() == originalTarget.edge()) {
                return slot;
            }
        }
        throw new IllegalStateException("Missing visible slot for stage index " + index);
    }

    public static int slotIndexForPair(SlotPair pair, SlotPair[] stagePairs) {
        for (int i = 0; i < stagePairs.length; i++) {
            if (stagePairs[i].equals(pair)) {
                return i;
            }
        }
        throw new IllegalStateException("Missing stage slot for pair " + pair);
    }

    public static void ensureCrossSolved(CubeState cube, Edge[] targetCross) {
        if (!isTargetCrossSolved(cube, targetCross)) {
            throw new IllegalArgumentException("F2L requires a solved D cross");
        }
    }

    public static boolean isTargetCrossSolved(CubeState cube, Edge[] targetCross) {
        for (var edge : targetCross) {
            if (cube.edgePerm[edge.ordinal()] != edge.ordinal() || cube.edgeOri[edge.ordinal()] != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isTargetSlotSolved(CubeState cube, TargetSlot slot) {
        return cube.cornerPerm[slot.corner().ordinal()] == slot.corner().ordinal()
                && cube.cornerOri[slot.corner().ordinal()] == 0
                && cube.edgePerm[slot.edge().ordinal()] == slot.edge().ordinal()
                && cube.edgeOri[slot.edge().ordinal()] == 0;
    }

    public static boolean isPairConnected(CubeState cube, SlotPair pair, CubeOrientation orientation) {
        var cornerPosition = findCornerPosition(cube, pair.cornerPiece());
        var edgePosition = findEdgePosition(cube, pair.edgePiece());
        if (!isAdjacent(cornerPosition, edgePosition)) {
            return false;
        }

        var facelets = CubeConverter.toFaceletStateAllowingCenterParity(cube);
        boolean adjacentOnSideFace = false;

        for (var cornerFace : cornerFaces(cornerPosition)) {
            var logicalFace = orientation.logicalFaceOf(cornerFace);
            if (logicalFace == Face.U || logicalFace == Face.D) {
                continue;
            }
            if (!edgeHasFace(edgePosition, cornerFace)) {
                continue;
            }

            adjacentOnSideFace = true;
            var cornerSticker = logicalCornerSticker(facelets, cornerPosition, cornerFace, orientation);
            var edgeSticker = logicalEdgeSticker(facelets, edgePosition, cornerFace, orientation);
            if (cornerSticker != edgeSticker) {
                return false;
            }
        }

        return adjacentOnSideFace;
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

    private static Corner findCornerPosition(CubeState cube, Corner targetCorner) {
        for (var position : Corner.values()) {
            if (cube.cornerPerm[position.ordinal()] == targetCorner.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing F2L corner: " + targetCorner);
    }

    private static Edge findEdgePosition(CubeState cube, Edge targetEdge) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == targetEdge.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing F2L edge: " + targetEdge);
    }

    private static boolean edgeHasFace(Edge edge, Face face) {
        var faces = edgeFaces(edge);
        return faces[0] == face || faces[1] == face;
    }

    private static boolean isAdjacent(Corner corner, Edge edge) {
        var cornerFaces = cornerFaces(corner);
        var edgeFaces = edgeFaces(edge);
        return contains(cornerFaces[0], cornerFaces[1], cornerFaces[2], edgeFaces[0])
                && contains(cornerFaces[0], cornerFaces[1], cornerFaces[2], edgeFaces[1]);
    }

    private static Face logicalCornerSticker(
            FaceletState facelets,
            Corner position,
            Face physicalPositionFace,
            CubeOrientation orientation
    ) {
        for (var sticker : CORNER_FACELETS[position.ordinal()]) {
            if (sticker.face() == physicalPositionFace) {
                return orientation.logicalFaceOf(facelets.getSticker(sticker.face(), sticker.index()));
            }
        }
        throw new IllegalArgumentException("Corner position " + position + " does not contain face " + physicalPositionFace);
    }

    private static Face logicalEdgeSticker(
            FaceletState facelets,
            Edge position,
            Face physicalPositionFace,
            CubeOrientation orientation
    ) {
        for (var sticker : EDGE_FACELETS[position.ordinal()]) {
            if (sticker.face() == physicalPositionFace) {
                return orientation.logicalFaceOf(facelets.getSticker(sticker.face(), sticker.index()));
            }
        }
        throw new IllegalArgumentException("Edge position " + position + " does not contain face " + physicalPositionFace);
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

    private static StickerRef sticker(Face face, int index) {
        return new StickerRef(face, index);
    }

    public record TargetSlot(Corner corner, Edge edge) {
    }

    public record SlotPair(Corner cornerPiece, Edge edgePiece) {
    }

    private record StickerRef(Face face, int index) {
    }
}
