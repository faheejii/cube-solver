package test;

import cfop.F2LGeometry;
import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LGeometryTest {
    @Test
    void isPairConnected_shouldRecognizeSolvedSlotAsConnectedPair() {
        var cube = new CubeState();
        var pair = new F2LGeometry.SlotPair(Corner.DFR, Edge.FR);

        assertTrue(F2LGeometry.isPairConnected(cube, pair, new CubeOrientation()));
    }

    @Test
    void isPairConnected_shouldRejectSeparatedTargetPieces() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U L");
        var pair = new F2LGeometry.SlotPair(Corner.DFR, Edge.FR);

        assertFalse(F2LGeometry.isPairConnected(cube, pair, new CubeOrientation()));
    }
}
