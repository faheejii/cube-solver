package test;

import algorithms.F2LCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.F2LSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LSolverTest {
    @Test
    void solveAfterCross_shouldUseDirectDatabaseCaseForBasicInsertOne() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R' U'");

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var solution = solver.solveAfterCross(cube, Face.D);

        assertEquals("U R U' R'", solution.toString());

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isF2LSolved(cube));
    }

    @Test
    void solveAfterCross_shouldUseDirectDatabaseCaseForBasicInsertThree() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R'");

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var solution = solver.solveAfterCross(cube, Face.D);

        assertEquals("R U' R'", solution.toString());

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isF2LSolved(cube));
    }

    @Test
    void solveAfterCross_shouldSolveCaseThatPreviouslyNeededAuf() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U' R' U' F U F'");
        assertTrue(CrossAnalyzer.isCrossSolved(cube));

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var solution = solver.solveAfterCross(cube, Face.D);

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isF2LSolved(cube));
    }

    @Test
    void solveAfterCross_shouldSupportSelectedFaceWhenCubeIsAlreadyNormalized() {
        assertSolveAfterCrossForSelectedFace("R U R' U'", Face.D, false);
        assertSolveAfterCrossForSelectedFace("R U' R'", Face.D, false);
        assertSolveAfterCrossForSelectedFace("R U R' U'", Face.U, true);
        assertSolveAfterCrossForSelectedFace("R U' R'", Face.U, true);
    }

    @Test
    void solveAfterCross_shouldUseDatabaseForUFaceCompositeCase() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "B' L B L2 D2 L D2");

        var crossSolver = new solver.CrossSolver();
        var crossSolution = crossSolver.solve(cube, Face.U);
        MoveApplier.executeMoves(cube, crossSolution.getMoves());

        assertTrue(CrossAnalyzer.isCrossSolved(cube, Face.U));

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var solution = solver.solveAfterCross(cube, Face.U);

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube, Face.U));
        assertTrue(F2LAnalyzer.isF2LSolved(cube, Face.U));
    }

    @Test
    void solveOnPersistentOrientedCube_shouldNotRepeatSelectedFacePrefix() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "B' L B L2 D2 L D2");
        var orientedCube = new OrientedCube(cube);

        var crossSolver = new solver.CrossSolver();
        var crossSolution = crossSolver.solve(orientedCube.cubeState(), Face.U);
        orientedCube.applyMoves(crossSolution.getMoves());

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var f2lSolution = solver.solve(orientedCube);

        assertFalse(f2lSolution.toString().startsWith("z2"),
                "persistent oriented stage should not restate the selected-face prefix");

        orientedCube.applyMoves(f2lSolution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(orientedCube.cubeState(), Face.U));
        assertTrue(F2LAnalyzer.isF2LSolved(orientedCube.cubeState(), Face.U));
    }

    @Test
    void solveAfterCross_shouldUseDatabaseForFFaceSingleSlotCase() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R B R'");

        var crossSolver = new solver.CrossSolver();
        var crossSolution = crossSolver.solve(cube, Face.F);
        MoveApplier.executeMoves(cube, crossSolution.getMoves());

        assertTrue(CrossAnalyzer.isCrossSolved(cube, Face.F));

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var solution = solver.solveAfterCross(cube, Face.F);

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube, Face.F));
        assertTrue(F2LAnalyzer.isF2LSolved(cube, Face.F));
    }

    private static void assertSolveAfterCrossForSelectedFace(String scramble, Face crossFace, boolean normalizeToSelectedFace) {
        var cube = new CubeState();
        if (normalizeToSelectedFace) {
            MoveApplier.executeAlgorithm(cube, "z2 " + scramble);
        } else {
            MoveApplier.applyAlgorithm(cube, scramble);
        }
        assertTrue(CrossAnalyzer.isCrossSolved(cube, crossFace),
                "cross should already be solved for face " + crossFace + " before F2L");

        var f2lSolver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var f2lSolution = f2lSolver.solveAfterCross(cube, crossFace);
        MoveApplier.executeMoves(cube, f2lSolution.getMoves());

        assertTrue(CrossAnalyzer.isCrossSolved(cube, crossFace),
                "cross broken for face " + crossFace + " by F2L solution " + f2lSolution + " from scramble " + scramble);
        assertTrue(F2LAnalyzer.isF2LSolved(cube, crossFace),
                "F2L not solved for face " + crossFace + " after " + f2lSolution + " from scramble " + scramble);
    }
}
