package test;

import api.SolveApiRequest;
import cube.Face;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SolveApiRequestTest {
    @Test
    void toSolveRequest_shouldParseColorNeutralAliases() {
        assertColorNeutral("CN");
        assertColorNeutral("Color Neutral");
        assertColorNeutral("COLOR_NEUTRAL");
    }

    @Test
    void toSolveRequest_shouldParseF2LMode() {
        var request = new SolveApiRequest("R U R'", "U", "optimized").toSolveRequest();

        assertEquals(solver.F2LMode.OPTIMIZED, request.f2lMode());
    }

    @Test
    void toSolveRequest_shouldDefaultF2LModeToGreedy() {
        var request = new SolveApiRequest("R U R'", "U").toSolveRequest();

        assertEquals(solver.F2LMode.GREEDY, request.f2lMode());
    }

    @Test
    void toSolveRequest_shouldDefaultBlankCrossFaceToU() {
        var request = new SolveApiRequest("R U R'", "").toSolveRequest();

        assertEquals(Face.U, request.crossFace());
        assertFalse(request.colorNeutralCross());
    }

    @Test
    void toSolveRequest_shouldParseConcreteFace() {
        var request = new SolveApiRequest("R U R'", "F").toSolveRequest();

        assertEquals(Face.F, request.crossFace());
        assertFalse(request.colorNeutralCross());
    }

    private static void assertColorNeutral(String value) {
        var request = new SolveApiRequest("R U R'", value).toSolveRequest();

        assertTrue(request.colorNeutralCross());
    }
}
