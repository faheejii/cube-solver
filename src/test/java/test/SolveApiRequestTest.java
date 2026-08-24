package test;

import api.CreateSolveJobRequest;
import api.SolveApiRequest;
import cube.Face;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void deadline_shouldDefaultTo15SecondsWhenOmitted() {
        var request = new SolveApiRequest("R U R'", "U");

        assertEquals(15L, request.deadlineSecondsOrDefault());
    }

    @Test
    void deadline_shouldAcceptConfiguredRange() {
        assertEquals(5L, new SolveApiRequest("R", "U", null, 5L).deadlineSeconds());
        assertEquals(120L, new SolveApiRequest("R", "U", null, 120L).deadlineSeconds());
    }

    @Test
    void solveJobRequest_shouldCarryDeadlineIntoSolveRequest() {
        var request = new CreateSolveJobRequest("R", "U", "optimized", null, false, 45L);

        assertEquals(45L, request.solveRequest().deadlineSeconds());
    }

    @Test
    void deepColorNeutral_shouldForceTheServerDeadlineAndReachTheSolver() {
        var request = new SolveApiRequest("R", "COLOR_NEUTRAL", "optimized", 15L, true);

        assertTrue(request.isDeepColorNeutralRequest());
        assertEquals(120L, request.effectiveDeadlineSeconds());
        assertTrue(request.toSolveRequest().deepColorNeutral());
        assertEquals(120L, request.toSolveRequest().optimizationDeadlineSeconds());
    }

    @Test
    void deepColorNeutral_shouldNotAffectNonMatchingSolveModes() {
        var fixed = new SolveApiRequest("R", "U", "optimized", 15L, true);
        var greedy = new SolveApiRequest("R", "COLOR_NEUTRAL", "greedy", 15L, true);

        assertFalse(fixed.isDeepColorNeutralRequest());
        assertEquals(15L, fixed.effectiveDeadlineSeconds());
        assertFalse(fixed.toSolveRequest().deepColorNeutral());
        assertFalse(greedy.isDeepColorNeutralRequest());
        assertEquals(15L, greedy.effectiveDeadlineSeconds());
        assertFalse(greedy.toSolveRequest().deepColorNeutral());
    }

    @Test
    void solveJobRequest_shouldCarryDeepColorNeutralIntoSolveRequest() {
        var request = new CreateSolveJobRequest("R", "CN", "optimized", null, false, 15L, true);

        assertTrue(request.solveRequest().toSolveRequest().deepColorNeutral());
        assertEquals(120L, request.solveRequest().toSolveRequest().optimizationDeadlineSeconds());
    }

    @Test
    void deadline_shouldRejectValuesOutsideSafeRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new SolveApiRequest("R", "U", null, 4L));
        assertThrows(IllegalArgumentException.class,
                () -> new SolveApiRequest("R", "U", null, 121L));
        assertThrows(IllegalArgumentException.class,
                () -> new CreateSolveJobRequest("R", "U", null, null, false, 121L));
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
