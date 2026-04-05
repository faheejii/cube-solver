package cfop;

import cube.Corner;
import cube.CubeState;
import cube.Edge;

import java.util.ArrayList;
import java.util.List;

public class F2LAnalyzer {
    public static boolean isF2LSolved(CubeState cube) {
        return countSolvedSlots(cube) == F2LSlot.values().length;
    }

    public static int countSolvedSlots(CubeState cube) {
        int solved = 0;
        for (var slot : F2LSlot.values()) {
            if (isSlotSolved(cube, slot)) {
                solved++;
            }
        }
        return solved;
    }

    public static boolean isSlotSolved(CubeState cube, F2LSlot slot) {
        return isCornerSolved(cube, slot.corner()) && isEdgeSolved(cube, slot.edge());
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
}
