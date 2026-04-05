package test;

import cube.Algorithm;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
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
        MoveApplier.applyMoves(cube, solution.getMoves());
        assertSelectedFaceCrossSolvedOnD(cube, Face.U);
    }

    @Test
    void solve_shouldProduceAlgorithmThatSolvesSelectedFaceCrossOnD() {
        assertSelectedFaceCrossSolvedAfterApplyingSolution("F", Face.D);
        assertSelectedFaceCrossSolvedAfterApplyingSolution("R U F", Face.D);
        assertSelectedFaceCrossSolvedAfterApplyingSolution("R U F' L2 D B'", Face.D);
        assertSelectedFaceCrossSolvedAfterApplyingSolution("F", Face.U);
        assertSelectedFaceCrossSolvedAfterApplyingSolution("R U F' L2 D B'", Face.U);
    }

    private static void assertSelectedFaceCrossSolvedAfterApplyingSolution(String scramble, Face crossFace) {
        var solver = new CrossSolver();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);

        Algorithm solution = solver.solve(cube, crossFace);

        MoveApplier.applyMoves(cube, solution.getMoves());
        assertSelectedFaceCrossSolvedOnD(cube, crossFace);
    }

    private static void assertSelectedFaceCrossSolvedOnD(CubeState cube, Face crossFace) {
        var targetEdges = targetEdgesForSelectedFace(crossFace);
        assertTrue(cube.edgePerm[Edge.DF.ordinal()] == targetEdges[0].ordinal() && cube.edgeOri[Edge.DF.ordinal()] == 0);
        assertTrue(cube.edgePerm[Edge.DR.ordinal()] == targetEdges[1].ordinal() && cube.edgeOri[Edge.DR.ordinal()] == 0);
        assertTrue(cube.edgePerm[Edge.DB.ordinal()] == targetEdges[2].ordinal() && cube.edgeOri[Edge.DB.ordinal()] == 0);
        assertTrue(cube.edgePerm[Edge.DL.ordinal()] == targetEdges[3].ordinal() && cube.edgeOri[Edge.DL.ordinal()] == 0);
    }

    private static Edge[] targetEdgesForSelectedFace(Face face) {
        var solved = new CubeState();
        var orientation = switch (face) {
            case D -> "";
            case U -> "z2";
            case R -> "z";
            case L -> "z'";
            case F -> "x";
            case B -> "x'";
        };
        MoveApplier.applyAlgorithm(solved, orientation);
        return new Edge[]{
                Edge.values()[solved.edgePerm[Edge.DF.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DR.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DB.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DL.ordinal()]]
        };
    }
}
