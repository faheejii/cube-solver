package test;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
import solver.CrossSolver;
import solver.F2LSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LSolverTest {
    @Test
    void solve_shouldThrowWhenCrossIsNotSolved() {
        var solver = new F2LSolver();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "F");

        assertThrows(IllegalArgumentException.class, () -> solver.solve(cube));
    }

    @Test
    void solveSlot_shouldReturnEmptyAlgorithmWhenSlotIsAlreadySolved() {
        var solver = new F2LSolver();

        Algorithm solution = solver.solveSlot(new CubeState(), F2LSlot.FR);

        assertEquals(0, solution.getMoveCount());
    }

    @Test
    void solveSlot_shouldSolveTargetSlotAndPreserveCross() {
        var solver = new F2LSolver();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R'");

        Algorithm solution = solver.solveSlot(cube, F2LSlot.FR);

        MoveApplier.applyMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isSlotSolved(cube, F2LSlot.FR));
    }

    @Test
    void solve_shouldSolveAllF2LSlotsWhenCrossIsSolved() {
        var solver = new F2LSolver();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R' U' F' U' F");

        assertTrue(CrossAnalyzer.isCrossSolved(cube));

        Algorithm solution = solver.solve(cube);

        MoveApplier.applyMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isF2LSolved(cube));
    }

    @Test
    void solve_withSelectedFace_shouldPrefixOrientation() {
        var solver = new F2LSolver();

        Algorithm solution = solver.solve(new CubeState(), Face.U);

        assertEquals("z2", solution.toString());
    }

    @Test
    void solve_afterSelectedFaceCross_shouldSolveF2LToo() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R'");

        var crossSolver = new CrossSolver();
        var crossSolution = crossSolver.solve(cube, Face.U);
        MoveApplier.applyMoves(cube, crossSolution.getMoves());

        var f2lSolver = new F2LSolver();
        var f2lSolution = f2lSolver.solveAfterCross(cube, Face.U);
        MoveApplier.applyMoves(cube, f2lSolution.getMoves());

        assertSelectedFaceF2LSolvedOnD(cube, Face.U);
    }

    private static void assertSelectedFaceF2LSolvedOnD(CubeState cube, Face crossFace) {
        var targetSlots = targetSlotsForSelectedFace(crossFace);
        for (var slot : targetSlots) {
            assertTrue(
                    cube.cornerPerm[slot.cornerPosition().ordinal()] == slot.cornerPiece().ordinal()
                            && cube.cornerOri[slot.cornerPosition().ordinal()] == 0
            );
            assertTrue(
                    cube.edgePerm[slot.edgePosition().ordinal()] == slot.edgePiece().ordinal()
                            && cube.edgeOri[slot.edgePosition().ordinal()] == 0
            );
        }
    }

    private static TargetSlot[] targetSlotsForSelectedFace(Face face) {
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
        return new TargetSlot[]{
                new TargetSlot(
                        Corner.DFR,
                        Corner.values()[solved.cornerPerm[Corner.DFR.ordinal()]],
                        Edge.FR,
                        Edge.values()[solved.edgePerm[Edge.FR.ordinal()]]
                ),
                new TargetSlot(
                        Corner.DLF,
                        Corner.values()[solved.cornerPerm[Corner.DLF.ordinal()]],
                        Edge.FL,
                        Edge.values()[solved.edgePerm[Edge.FL.ordinal()]]
                ),
                new TargetSlot(
                        Corner.DBL,
                        Corner.values()[solved.cornerPerm[Corner.DBL.ordinal()]],
                        Edge.BL,
                        Edge.values()[solved.edgePerm[Edge.BL.ordinal()]]
                ),
                new TargetSlot(
                        Corner.DRB,
                        Corner.values()[solved.cornerPerm[Corner.DRB.ordinal()]],
                        Edge.BR,
                        Edge.values()[solved.edgePerm[Edge.BR.ordinal()]]
                )
        };
    }

    private record TargetSlot(Corner cornerPosition, Corner cornerPiece, Edge edgePosition, Edge edgePiece) {
    }
}
