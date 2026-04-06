package test;

import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
import cfop.CrossAnalyzer;
import solver.CrossSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrossSolverTest {
    @Test
    void solve_shouldReturnEmptyAlgorithmWhenDCrossIsAlreadySolved() {
        var solver = new CrossSolver();

        Algorithm solution = solver.solve(new CubeState());

        assertEquals(0, solution.getMoveCount());
    }

    @Test
    void solve_withSelectedFace_shouldRotateThatFaceToDAndThenSolveThatCross() {
        var solver = new CrossSolver();

        Algorithm solution = solver.solve(new CubeState(), Face.U);

        assertEquals("z2", solution.toString());

        var cube = new CubeState();
        MoveApplier.executeMoves(cube, solution.getMoves());
        assertSelectedFaceCrossSolvedOnD(cube, Face.U);
    }

    @Test
    void solve_shouldProduceAlgorithmThatSolvesSelectedFaceCrossOnD() {
        for (var face : Face.values()) {
            assertSelectedFaceCrossSolvedAfterApplyingSolution("F", face);
            assertSelectedFaceCrossSolvedAfterApplyingSolution("R U F", face);
            assertSelectedFaceCrossSolvedAfterApplyingSolution("R U F' L2 D B'", face);
        }
    }

    private static void assertSelectedFaceCrossSolvedAfterApplyingSolution(String scramble, Face crossFace) {
        var solver = new CrossSolver();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);

        Algorithm solution = solver.solve(cube, crossFace);

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube, crossFace),
                "cross not solved for face " + crossFace + " after " + solution + " on scramble " + scramble);
    }

    private static void assertSelectedFaceCrossSolvedOnD(CubeState cube, Face crossFace) {
        assertTrue(CrossAnalyzer.isCrossSolved(cube, crossFace));
    }
}
