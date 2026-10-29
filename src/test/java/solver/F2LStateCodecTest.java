package solver;

import cfop.F2LGeometry.TargetSlot;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Move;
import cube.MoveApplier;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class F2LStateCodecTest {
    private static final TargetSlot[] TARGETS = cfop.F2LGeometry.targetSlotsForOrientation(new CubeOrientation());

    @Test
    void completeStateKey_shouldIncludeLastLayerPieces() {
        var solved = new CubeState();
        var turnedTop = solved.copy();
        MoveApplier.applyAlgorithm(turnedTop, "U");

        var solvedKey = F2LStateCodec.completeStateKey(
                solved, new CubeOrientation(), TARGETS, List.of(TARGETS)
        );
        var turnedKey = F2LStateCodec.completeStateKey(
                turnedTop, new CubeOrientation(), TARGETS, List.of(TARGETS)
        );

        assertNotEquals(solvedKey, turnedKey);
    }

    @Test
    void completeStateKey_shouldIncludeFrameAndProtectedSlots() {
        var cube = new CubeState();
        var orientation = new CubeOrientation();
        var base = F2LStateCodec.completeStateKey(cube, orientation, TARGETS, List.of(TARGETS[0]));

        var rotated = orientation.copy();
        rotated.applyRotation(Move.Y);
        var rotatedKey = F2LStateCodec.completeStateKey(cube, rotated, TARGETS, List.of(TARGETS[0]));
        var protectedKey = F2LStateCodec.completeStateKey(cube, orientation, TARGETS, List.of(TARGETS[1]));

        assertNotEquals(base, rotatedKey);
        assertNotEquals(base, protectedKey);
        assertEquals(base, F2LStateCodec.completeStateKey(cube.copy(), orientation.copy(), TARGETS,
                List.of(TARGETS[0])));
    }
}
