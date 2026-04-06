package cfop;

import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.OrientationFrames;

import java.util.ArrayList;
import java.util.List;

public class F2LAnalyzer {
    public static boolean isF2LSolved(CubeState cube) {
        return countSolvedSlots(cube, new CubeOrientation()) == F2LSlot.values().length;
    }

    public static int countSolvedSlots(CubeState cube) {
        return countSolvedSlots(cube, new CubeOrientation());
    }

    public static boolean isF2LSolved(CubeState cube, Face crossFace) {
        return countSolvedSlots(cube, OrientationFrames.orientedFrameFor(crossFace)) == F2LSlot.values().length;
    }

    public static boolean isF2LSolved(CubeState cube, CubeOrientation orientation) {
        return countSolvedSlots(cube, orientation) == F2LSlot.values().length;
    }

    public static int countSolvedSlots(CubeState cube, Face crossFace) {
        return countSolvedSlots(cube, OrientationFrames.orientedFrameFor(crossFace));
    }

    public static int countSolvedSlots(CubeState cube, CubeOrientation orientation) {
        int solved = 0;
        for (var slot : F2LSlot.values()) {
            if (isSlotSolved(cube, slot, orientation)) {
                solved++;
            }
        }
        return solved;
    }

    public static boolean isSlotSolved(CubeState cube, F2LSlot slot) {
        return isCornerSolved(cube, slot.corner()) && isEdgeSolved(cube, slot.edge());
    }

    public static List<F2LSlot> getSolvedSlots(CubeState cube, Face crossFace) {
        return getSolvedSlots(cube, OrientationFrames.orientedFrameFor(crossFace));
    }

    public static List<F2LSlot> getSolvedSlots(CubeState cube, CubeOrientation orientation) {
        var solved = new ArrayList<F2LSlot>();
        for (var slot : F2LSlot.values()) {
            if (isSlotSolved(cube, slot, orientation)) {
                solved.add(slot);
            }
        }
        return List.copyOf(solved);
    }

    public static List<F2LSlot> getUnsolvedSlots(CubeState cube) {
        var unsolved = new ArrayList<F2LSlot>();
        for (var slot : F2LSlot.values()) {
            if (!isSlotSolved(cube, slot)) {
                unsolved.add(slot);
            }
        }
        return List.copyOf(unsolved);
    }

    private static boolean isCornerSolved(CubeState cube, Corner corner) {
        return cube.cornerPerm[corner.ordinal()] == corner.ordinal() && cube.cornerOri[corner.ordinal()] == 0;
    }

    private static boolean isEdgeSolved(CubeState cube, Edge edge) {
        return cube.edgePerm[edge.ordinal()] == edge.ordinal() && cube.edgeOri[edge.ordinal()] == 0;
    }

    private static boolean isPlacedSlotSolved(CubeState cube, TargetSlot slot) {
        return cube.cornerPerm[slot.cornerPosition().ordinal()] == slot.cornerPiece().ordinal()
                && cube.cornerOri[slot.cornerPosition().ordinal()] == 0
                && cube.edgePerm[slot.edgePosition().ordinal()] == slot.edgePiece().ordinal()
                && cube.edgeOri[slot.edgePosition().ordinal()] == 0;
    }

    private static boolean isSlotSolved(CubeState cube, F2LSlot slot, CubeOrientation orientation) {
        return isPlacedSlotSolved(cube, targetSlotFor(slot, orientation));
    }

    private static TargetSlot targetSlotFor(F2LSlot slot, CubeOrientation orientation) {
        return switch (slot) {
            case FR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.R)),
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.R))
            );
            case FL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.L)),
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.L))
            );
            case BL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.L)),
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.L))
            );
            case BR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.R)),
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.R))
            );
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

    private record TargetSlot(Corner cornerPosition, Corner cornerPiece, Edge edgePosition, Edge edgePiece) {
    }
}
