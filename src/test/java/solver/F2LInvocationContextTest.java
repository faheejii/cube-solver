package solver;

import cfop.F2LSlot;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Move;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class F2LInvocationContextTest {
    @Test
    void prefixCache_shouldReturnIndependentMutableCopies() {
        var context = new F2LInvocationContext();
        var source = new CubeState();
        var first = context.prefix(source, new CubeOrientation(), List.of(Move.R, Move.U));
        var expected = first.cube().copy();
        MoveApplier.applyAlgorithm(first.cube(), "F2");

        var second = context.prefix(source, new CubeOrientation(), List.of(Move.R, Move.U));

        assertNotSame(first.cube(), second.cube());
        assertEquals(expected.cornerPerm[0], second.cube().cornerPerm[0]);
        assertEquals(expected.cornerOri[0], second.cube().cornerOri[0]);
        assertEquals(expected.edgePerm[0], second.cube().edgePerm[0]);
        assertEquals(expected.edgeOri[0], second.cube().edgeOri[0]);
    }

    @Test
    void recognitionCache_shouldReuseSignaturesAndRespectTheFrame() {
        var context = new F2LInvocationContext();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U F2");
        var orientation = new CubeOrientation();
        var signature = context.signature(cube, F2LSlot.FR, orientation);

        assertEquals(signature, context.signature(cube, F2LSlot.FR, orientation.copy()));
        orientation.applyRotation(Move.Y);
        assertNotSame(signature, context.signature(cube, F2LSlot.FR, orientation));
    }

    @Test
    void preservationMaskCache_shouldDistinguishFrameAndProtectedSlots() {
        var context = new F2LInvocationContext();
        var orientation = new CubeOrientation();
        var targets = cfop.F2LGeometry.targetSlotsForOrientation(orientation);
        var first = context.preservationMask(List.of(targets[0]), orientation);
        assertEquals(F2LStateCodec.preservationMaskFor(List.of(targets[0]), orientation), first);
        var same = context.preservationMask(List.of(targets[0]), orientation.copy());
        var other = context.preservationMask(List.of(targets[1]), orientation);
        var rotated = orientation.copy();
        rotated.applyRotation(Move.Y);

        assertEquals(first, same);
        assertNotSame(first, other);
        assertNotSame(first, context.preservationMask(List.of(targets[0]), rotated));
    }
}
