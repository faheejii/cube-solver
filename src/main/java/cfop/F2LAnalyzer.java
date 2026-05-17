package cfop;

import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.OrientationFrames;

import java.util.ArrayList;
import java.util.List;

import static cfop.F2LGeometry.isTargetSlotSolved;
import static cfop.F2LGeometry.targetSlotFor;

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
        return isTargetSlotSolved(cube, targetSlotFor(slot, orientation));
    }
}
