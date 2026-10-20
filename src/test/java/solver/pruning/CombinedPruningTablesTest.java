package solver.pruning;

import cfop.F2LSlot;
import cube.CubeState;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
import solver.PruningTables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombinedPruningTablesTest {
    @Test
    void bundledTableHasZeroDistanceForEverySolvedSlot() {
        var solved = new CubeState();
        for (var slot : F2LSlot.values()) assertEquals(0, PruningTables.combinedDistance(solved, slot));
    }

    @Test
    void everyFaceTurnChangesTheAbstractDistanceByAtMostOne() {
        var state = new CubeState();
        MoveApplier.applyAlgorithm(state, "R U F2 L' D B U2 R2 F'");
        for (var slot : F2LSlot.values()) {
            var distance = PruningTables.combinedDistance(state, slot);
            for (var move : CombinedPruningCoordinates.MOVES) {
                var moved = state.copy();
                MoveApplier.applyMove(moved, move);
                var movedDistance = PruningTables.combinedDistance(moved, slot);
                assertTrue(Math.abs(distance - movedDistance) <= 1,
                        () -> slot + " distance changed by more than one after " + move + ": " + distance + " -> " + movedDistance);
            }
        }
    }

    @Test
    void tableLookupUsesTheSameDistanceAfterDFrameRotationAndSlotRotation() {
        var state = new CubeState();
        MoveApplier.applyAlgorithm(state, "F R U2 B' L D2");
        for (var slot : F2LSlot.values()) {
            var rotated = CombinedPruningCoordinates.rotateFrameY(state);
            var rotatedSlot = CombinedPruningCoordinates.rotateSlotY(slot);
            assertEquals(PruningTables.combinedDistance(state, slot),
                    PruningTables.combinedDistance(rotated, rotatedSlot));
        }
    }

    @Test
    void combinedLowerBoundsMatchTheFourRotationallyEquivalentSlotLookups() {
        var state = new CubeState();
        MoveApplier.applyAlgorithm(state, "R U F2 L' D B U2");
        var distances = PruningTables.combinedDistances(state);
        for (var slot : F2LSlot.values()) {
            assertEquals(PruningTables.combinedDistance(state, slot), distances[slot.ordinal()], slot.name());
        }
        assertEquals(java.util.Arrays.stream(distances).min().orElseThrow(),
                PruningTables.xcrossLowerBound(state));

        var xxcrossBound = Integer.MAX_VALUE;
        for (var first = 0; first < distances.length; first++) {
            for (var second = first + 1; second < distances.length; second++) {
                xxcrossBound = Math.min(xxcrossBound, Math.max(distances[first], distances[second]));
            }
        }
        assertEquals(xxcrossBound, PruningTables.xxcrossLowerBound(state));
    }
}
