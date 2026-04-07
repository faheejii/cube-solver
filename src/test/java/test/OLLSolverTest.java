package test;

import algorithms.OLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cube.CubeState;
import cube.Algorithm;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
import solver.OLLSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OLLSolverTest {
    @Test
    void solve_shouldReturnEmptyAlgorithmWhenOllAlreadySolved() {
        var database = OLLCaseDatabase.empty();
        database.register("R U R' U R U2 R'", "sune");
        var solver = new OLLSolver(database);

        assertEquals("", solver.solve(new CubeState()).toString());
    }

    @Test
    void solve_shouldUseDatabaseCaseForKnownOll() {
        var cube = new CubeState();
        var algorithm = "R U R' U R U2 R'";
        MoveApplier.applyMoves(cube, Algorithm.parse(algorithm).inverse().getMoves());

        var database = OLLCaseDatabase.empty();
        database.register(algorithm, "sune");
        var solver = new OLLSolver(database);
        var solution = solver.solve(cube);

        assertEquals(algorithm, solution.toString());

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(F2LAnalyzer.isF2LSolved(cube));
        assertTrue(OLLAnalyzer.isOllSolved(cube));
    }
}
