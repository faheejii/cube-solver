package cfop;

import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.LogicalFaceletView;
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
        var view = LogicalFaceletView.of(cube, orientation);
        int solved = 0;
        for (var slot : F2LSlot.values()) {
            if (isLogicalSlotSolved(view, slot)) {
                solved++;
            }
        }
        return solved;
    }

    public static boolean isSlotSolved(CubeState cube, F2LSlot slot) {
        return isSlotSolved(cube, slot, new CubeOrientation());
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

    private static boolean isSlotSolved(CubeState cube, F2LSlot slot, CubeOrientation orientation) {
        return isLogicalSlotSolved(LogicalFaceletView.of(cube, orientation), slot);
    }

    private static boolean isLogicalSlotSolved(LogicalFaceletView view, F2LSlot slot) {
        return switch (slot) {
            case FR -> view.sticker(Face.D, 2) == Face.D
                    && view.sticker(Face.F, 8) == Face.F
                    && view.sticker(Face.R, 6) == Face.R
                    && view.sticker(Face.F, 5) == Face.F
                    && view.sticker(Face.R, 3) == Face.R;
            case FL -> view.sticker(Face.D, 0) == Face.D
                    && view.sticker(Face.F, 6) == Face.F
                    && view.sticker(Face.L, 8) == Face.L
                    && view.sticker(Face.F, 3) == Face.F
                    && view.sticker(Face.L, 5) == Face.L;
            case BL -> view.sticker(Face.D, 6) == Face.D
                    && view.sticker(Face.B, 8) == Face.B
                    && view.sticker(Face.L, 6) == Face.L
                    && view.sticker(Face.B, 5) == Face.B
                    && view.sticker(Face.L, 3) == Face.L;
            case BR -> view.sticker(Face.D, 8) == Face.D
                    && view.sticker(Face.B, 6) == Face.B
                    && view.sticker(Face.R, 8) == Face.R
                    && view.sticker(Face.B, 3) == Face.B
                    && view.sticker(Face.R, 5) == Face.R;
        };
    }
}
