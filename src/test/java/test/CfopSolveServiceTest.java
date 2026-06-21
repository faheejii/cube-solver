package test;

import org.junit.jupiter.api.Test;
import solver.CfopSolveRequest;
import solver.CfopSolveService;
import solver.F2LMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfopSolveServiceTest {
    @Test
    void solve_shouldReturnStructuredTwoPhaseResult() {
        var service = new CfopSolveService();

        var result = service.solve(new CfopSolveRequest("R D R' D2 R D' R'", cube.Face.U));

        assertEquals("z2", result.cross().algorithm());
        assertTrue(result.f2lSetupCaseCount() > 0);
        assertTrue(result.f2lInsertCaseCount() > 0);
        assertTrue(result.cross().solved());
        assertTrue(result.f2l().solved());
        assertTrue(result.fullySolved());
    }

    @Test
    void solve_withOptimizedF2LMode_shouldReturnSolvedResultNoLongerThanFastCfop() {
        var service = new CfopSolveService();
        var scramble = "R D R' D2 R D' R'";

        var greedy = service.solve(new CfopSolveRequest(scramble, cube.Face.U, F2LMode.GREEDY));
        var optimized = service.solve(new CfopSolveRequest(scramble, cube.Face.U, F2LMode.OPTIMIZED));

        assertEquals("optimized", optimized.f2lMode());
        assertTrue(optimized.f2l().solved());
        assertTrue(optimized.fullySolved());
        assertTrue(totalCfopMoves(optimized) <= totalCfopMoves(greedy));
    }

    @Test
    void solve_withColorNeutralCross_shouldReturnChosenConcreteFace() {
        var service = new CfopSolveService();

        var result = service.solve(CfopSolveRequest.colorNeutral("R D R' D2 R D' R'"));

        assertTrue(java.util.Set.of("U", "D", "F", "B", "L", "R").contains(result.crossFace()));
        assertTrue(result.cross().solved());
        assertTrue(result.f2l().solved());
        assertTrue(result.fullySolved());
    }

    private static int totalCfopMoves(solver.CfopSolveResult result) {
        return result.cross().moveCount()
                + result.f2l().moveCount()
                + result.oll().moveCount()
                + result.pll().moveCount();
    }
}
