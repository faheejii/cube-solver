package solver;

import cfop.F2LGeometry.TargetSlot;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.OrientedCube;

import java.util.ArrayList;
import java.util.List;

/** Encodes the state facts used by F2L search and database preservation checks. */
final class F2LStateCodec {
    private F2LStateCodec() {
    }

    static CompleteStateKey completeStateKey(
            CubeState cube,
            CubeOrientation orientation,
            TargetSlot[] targetSlots,
            List<TargetSlot> protectedSlots
    ) {
        var cubeKey = cubeKey(cube);
        int protectedMask = 0;
        for (int index = 0; index < targetSlots.length; index++) {
            if (protectedSlots.contains(targetSlots[index])) {
                protectedMask |= 1 << index;
            }
        }
        int frame = orientation.faceAt(Face.U).ordinal()
                | (orientation.faceAt(Face.R).ordinal() << 3)
                | (orientation.faceAt(Face.F).ordinal() << 6);
        return new CompleteStateKey(cubeKey.corners(), cubeKey.edges(), frame, protectedMask);
    }

    static CubeKey cubeKey(CubeState cube) {
        long corners = 0L;
        int cornerShift = 0;
        for (int position = 0; position < cube.cornerPerm.length; position++) {
            corners |= (long) (cube.cornerPerm[position] & 0x7) << cornerShift;
            cornerShift += 3;
            corners |= (long) (cube.cornerOri[position] & 0x3) << cornerShift;
            cornerShift += 2;
        }

        long edges = 0L;
        int edgeShift = 0;
        for (int position = 0; position < cube.edgePerm.length; position++) {
            edges |= (long) (cube.edgePerm[position] & 0xf) << edgeShift;
            edgeShift += 4;
            edges |= (long) (cube.edgeOri[position] & 0x1) << edgeShift;
            edgeShift++;
        }

        return new CubeKey(corners, edges);
    }

    record CubeKey(long corners, long edges) {
    }

    record CompleteStateKey(long corners, long edges, int frame, int protectedMask) {
    }

    static F2LPreservationMask preservationMaskFor(List<TargetSlot> protectedSlots, CubeOrientation orientation) {
        var slots = new ArrayList<F2LSlot>();
        for (var protectedSlot : protectedSlots) {
            slots.add(cfop.F2LGeometry.visibleSlotForTarget(protectedSlot, orientation));
        }
        return F2LPreservationMask.of(slots);
    }

    static CubeOrientation executeAndReturnOrientation(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var orientedCube = new OrientedCube(cube, orientation);
        orientedCube.applyMoves(moves);
        return orientedCube.orientation();
    }

    static List<TargetSlot> relevantSlots(TargetSlot targetSlot, List<TargetSlot> protectedSlots) {
        var slots = new ArrayList<TargetSlot>(protectedSlots.size() + 1);
        slots.add(targetSlot);
        for (var slot : protectedSlots) {
            if (slot != targetSlot) {
                slots.add(slot);
            }
        }
        return slots;
    }

}
