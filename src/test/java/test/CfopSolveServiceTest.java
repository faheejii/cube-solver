package test;

import org.junit.jupiter.api.Test;
import solver.CfopSolveRequest;
import solver.CfopSolveService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfopSolveServiceTest {
    @Test
    void solve_shouldReturnStructuredTwoPhaseResult() {
        var service = new CfopSolveService();

        var result = service.solve(new CfopSolveRequest("R D R' D2 R D' R'", cube.Face.U, false));

        assertEquals("z2", result.cross().algorithm());
        assertTrue(result.f2lSetupCaseCount() > 0);
        assertTrue(result.f2lInsertCaseCount() > 0);
        assertEquals("two-phase DB + fallback", result.f2lMode());
        assertTrue(result.cross().solved());
        assertTrue(result.f2l().solved());
        assertTrue(result.fullySolved());
    }
}
