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
        assertEquals("y2 R U R' U2 R U' R'", result.f2l().algorithm());
        assertTrue(result.cross().solved());
        assertTrue(result.f2l().solved());
        assertTrue(result.fullySolved());
    }
}
