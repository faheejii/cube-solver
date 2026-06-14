package test;

import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.Move;
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

    @Test
    void solve_shouldAvoidBMovesInCrossBodyForAllSelectedFaces() {
        for (var face : Face.values()) {
            assertCrossBodyHasNoBMoves("R U F' L2 D B'", face);
            assertCrossBodyHasNoBMoves("L2 B2 D L2 B2 D' R2 U' L2 B2 D2 F' U' F D' L B U' R' U2", face);
        }
    }

    @Test
    void solve_withSelectedFace_shouldKeepSolvedUFaceCrossAtZ2Only() {
        var solver = new CrossSolver();

        Algorithm solution = solver.solve(new CubeState(), Face.U);

        assertEquals("z2", solution.toString());
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

    private static void assertCrossBodyHasNoBMoves(String scramble, Face crossFace) {
        var solver = new CrossSolver();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);

        Algorithm solution = solver.solve(cube, crossFace);

        for (var move : crossBodyMoves(solution, crossFace)) {
            assertTrue(move != Move.B && move != Move.B2 && move != Move.B_PRIME,
                    "cross body should not contain B moves for face " + crossFace
                            + " after " + solution + " on scramble " + scramble);
        }

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube, crossFace),
                "cross not solved for face " + crossFace + " after " + solution + " on scramble " + scramble);
    }

    private static java.util.List<Move> crossBodyMoves(Algorithm solution, Face crossFace) {
        var moves = solution.getMoves();
        var index = orientationPrefixLength(crossFace);
        if (index < moves.size() && isYMove(moves.get(index))) {
            index++;
        }
        return moves.subList(index, moves.size());
    }

    private static int orientationPrefixLength(Face face) {
        return switch (face) {
            case D -> 0;
            case U, R, L, F, B -> 1;
        };
    }

    private static boolean isYMove(Move move) {
        return move == Move.Y || move == Move.Y2 || move == Move.Y_PRIME;
    }

    private static void assertSelectedFaceCrossSolvedOnD(CubeState cube, Face crossFace) {
        assertTrue(CrossAnalyzer.isCrossSolved(cube, crossFace));
    }
}
