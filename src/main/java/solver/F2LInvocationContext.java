package solver;

import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry.TargetSlot;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Move;
import io.CubeConverter;
import io.FaceletState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-call immutable recognition and prefix cache; never shared across solver jobs. */
final class F2LInvocationContext {
    private final Map<F2LStateCodec.CubeKey, FaceletState> facelets = new HashMap<>();
    private final Map<SignatureKey, F2LCaseSignature> signatures = new HashMap<>();
    private final Map<MaskKey, F2LPreservationMask> masks = new HashMap<>();
    private final Map<PrefixKey, PrefixState> prefixes = new HashMap<>();

    F2LCaseSignature signature(CubeState cube, F2LSlot slot, CubeOrientation orientation) {
        var key = new SignatureKey(F2LStateCodec.cubeKey(cube), slot, frame(orientation));
        return signatures.computeIfAbsent(key, ignored -> F2LCaseSignatureExtractor.extract(
                cube, slot, orientation, facelets.computeIfAbsent(
                        key.cube(), unused -> CubeConverter.toFaceletStateAllowingCenterParity(cube)
                )
        ));
    }

    F2LPreservationMask preservationMask(List<TargetSlot> protectedSlots, CubeOrientation orientation) {
        int mask = 0;
        for (int index = 0; index < F2LSlot.values().length; index++) {
            var slot = F2LSlot.values()[index];
            var target = cfop.F2LGeometry.targetSlotFor(slot, orientation);
            if (protectedSlots.contains(target)) mask |= 1 << index;
        }
        final int protectedMask = mask;
        var key = new MaskKey(frame(orientation), protectedMask);
        return masks.computeIfAbsent(key, ignored -> new F2LPreservationMask(protectedMask));
    }

    PrefixState prefix(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var key = new PrefixKey(F2LStateCodec.cubeKey(cube), frame(orientation), List.copyOf(moves));
        var cached = prefixes.computeIfAbsent(key, ignored -> {
            var resultCube = cube.copy();
            var resultOrientation = F2LStateCodec.executeAndReturnOrientation(resultCube, orientation, moves);
            return new PrefixState(resultCube, resultOrientation);
        });
        return new PrefixState(cached.cube().copy(), cached.orientation().copy());
    }

    private static int frame(CubeOrientation orientation) {
        return orientation.faceAt(cube.Face.U).ordinal()
                | (orientation.faceAt(cube.Face.R).ordinal() << 3)
                | (orientation.faceAt(cube.Face.F).ordinal() << 6);
    }

    private record SignatureKey(F2LStateCodec.CubeKey cube, F2LSlot slot, int frame) {
    }

    private record MaskKey(int frame, int protectedMask) {
    }

    private record PrefixKey(F2LStateCodec.CubeKey cube, int frame, List<Move> moves) {
    }

    record PrefixState(CubeState cube, CubeOrientation orientation) {
    }
}
