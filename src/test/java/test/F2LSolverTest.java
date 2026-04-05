package test;

import algorithms.F2LCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
import solver.F2LSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LSolverTest {
    @Test
    void solveAfterCross_shouldUseDirectDatabaseCaseForBasicInsertOne() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R' U'");

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var solution = solver.solveAfterCross(cube, Face.D);

        assertEquals("U R U' R'", solution.toString());

        MoveApplier.applyMoves(cube, solution.getMoves());
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

        MoveApplier.applyMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isF2LSolved(cube));
    }

    @Test
    void solveAfterCross_shouldTryAufBeforeFallingBackToSearch() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U' R' U' F U F'");
        assertTrue(CrossAnalyzer.isCrossSolved(cube));

        var solver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
        var solution = solver.solveAfterCross(cube, Face.D);

        assertTrue(solution.toString().startsWith("U "), "expected AUF-first solution, got: " + solution);

        MoveApplier.applyMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isF2LSolved(cube));
    }
}
