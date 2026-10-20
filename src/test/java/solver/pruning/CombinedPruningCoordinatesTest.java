package solver.pruning;

import cfop.F2LSlot;
import cube.CubeState;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CombinedPruningCoordinatesTest {
    @Test
    void coordinateHasExpectedCompactCardinalityAndRoundTrips() {
        assertEquals(72_990_720, CombinedPruningCoordinates.STATE_COUNT);
        for (var index : new int[]{0, 1, 15, 16, 123_456, CombinedPruningCoordinates.STATE_COUNT - 1}) {
            var decoded = CombinedPruningCoordinates.decode(index);
            assertEquals(index, CombinedPruningCoordinates.encode(decoded[0], decoded[1], decoded[2], decoded[3]));
        }
    }

    @Test
    void solvedCubeMapsToTheCombinedGoalFromEveryCanonicalSlot() {
        var solved = new CubeState();
        var goal = CombinedPruningCoordinates.solvedIndex();
        for (var slot : F2LSlot.values()) assertEquals(goal, CombinedPruningCoordinates.index(solved, slot), slot.name());
    }

    @Test
    void dAxisFrameRotationKeepsTheAbstractDistanceCoordinateEquivalent() {
        var state = new CubeState();
        MoveApplier.applyAlgorithm(state, "R U F2 L' D B U2");
        for (var slot : F2LSlot.values()) {
            var originalIndex = CombinedPruningCoordinates.index(state, slot);
            var rotatedState = state;
            var rotatedSlot = slot;
            for (var turn = 0; turn < 4; turn++) {
                rotatedState = CombinedPruningCoordinates.rotateFrameY(rotatedState);
                rotatedSlot = CombinedPruningCoordinates.rotateSlotY(rotatedSlot);
                assertEquals(originalIndex, CombinedPruningCoordinates.index(rotatedState, rotatedSlot));
            }
            assertEquals(originalIndex, CombinedPruningCoordinates.index(state, slot));
        }
    }

    @Test
    void trackedCrossAndPairMovesChangeTheCombinedCoordinate() {
        var state = new CubeState();
        var initial = CombinedPruningCoordinates.index(state, F2LSlot.FR);
        MoveApplier.applyAlgorithm(state, "R U F");
        assertNotEquals(initial, CombinedPruningCoordinates.index(state, F2LSlot.FR));
    }
}
