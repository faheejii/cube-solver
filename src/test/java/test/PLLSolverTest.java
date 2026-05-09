package test;

import algorithms.PLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.CrossSolver;
import solver.F2LSolver;
import solver.OLLSolver;
import solver.PLLSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PLLSolverTest {
    @Test
    void solve_shouldReturnEmptyAlgorithmWhenPllAlreadySolved() {
        var database = PLLCaseDatabase.empty();
        database.register("R U R' U' R' F R2 U' R' U' R U R' F'", "t");
        var solver = new PLLSolver(database);

        assertEquals("", solver.solve(new CubeState()).toString());
    }

    @Test
    void solve_shouldUseDatabaseCaseForKnownPll() {
        var cube = new OrientedCube();
        var algorithm = "R U R' U' R' F R2 U' R' U' R U R' F'";
        cube.applyMoves(Algorithm.parse(algorithm).inverse().getMoves());

        var database = PLLCaseDatabase.empty();
        database.register(algorithm, "t");
        var solver = new PLLSolver(database);
        var solution = solver.solve(cube);

        assertEquals(algorithm, solution.toString());

        cube.applyMoves(solution.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube.cubeState(), cube.orientation()));
        assertTrue(F2LAnalyzer.isF2LSolved(cube.cubeState(), cube.orientation()));
        assertTrue(OLLAnalyzer.isOllSolved(cube.cubeState(), cube.orientation()));
        assertTrue(PLLAnalyzer.isPllSolved(cube.cubeState(), cube.orientation()));
    }

    @Test
    void solve_shouldTryAufBeforeLookup() {
        var cube = new OrientedCube();
        var algorithm = "R U R' U' R' F R2 U' R' U' R U R' F'";
        cube.applyMoves(Algorithm.parse(Algorithm.parse(algorithm).inverse() + " U'").getMoves());

        var database = PLLCaseDatabase.empty();
        database.register(algorithm, "t");
        var solver = new PLLSolver(database);
        var solution = solver.solve(cube);

        cube.applyMoves(solution.getMoves());
        assertTrue(PLLAnalyzer.isPllSolved(cube.cubeState(), cube.orientation()));
    }

    @Test
    void solve_shouldReturnFinalAufWhenOnlyAufRemains() {
        var cube = new OrientedCube();
        cube.applyAlgorithm("U");

        var solver = new PLLSolver(PLLCaseDatabase.seedCases());
        var solution = solver.solve(cube);

        cube.applyMoves(solution.getMoves());
        assertTrue(PLLAnalyzer.isPllSolved(cube.cubeState(), cube.orientation()));
    }

    @Test
    void solve_shouldHandleFPermWithFinalAuf() {
        var cube = solveThroughOll("F' L' U2 B L' D B U' F2 R2 F2 R U2 D2 L' D2 F2 L2 D2 F");

        var solution = new PLLSolver(PLLCaseDatabase.seedCases()).solve(cube);

        cube.applyMoves(solution.getMoves());
        assertTrue(isFullySolved(cube));
    }

    @Test
    void solve_shouldHandleAbPermWithFinalAuf() {
        var cube = solveThroughOll("U2 R2 B2 D' L2 U F2 D2 R2 B2 R2 D F' R' B2 L' B' R2 B2 L' D2");

        var solution = new PLLSolver(PLLCaseDatabase.seedCases()).solve(cube);

        cube.applyMoves(solution.getMoves());
        assertTrue(isFullySolved(cube));
    }

    private static OrientedCube solveThroughOll(String scramble) {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);
        var orientedCube = new OrientedCube(cube);

        var crossSolution = new CrossSolver().solve(orientedCube.cubeState(), Face.U);
        orientedCube.applyMoves(crossSolution.getMoves());

        var f2lSolution = new F2LSolver(algorithms.F2LCaseDatabase.seedBasicCases()).solve(orientedCube);
        orientedCube.applyMoves(f2lSolution.getMoves());

        var ollSolution = new OLLSolver(algorithms.OLLCaseDatabase.seedCases()).solve(orientedCube);
        orientedCube.applyMoves(ollSolution.getMoves());

        return orientedCube;
    }

    private static boolean isFullySolved(OrientedCube cube) {
        return CrossAnalyzer.isCrossSolved(cube.cubeState(), cube.orientation())
                && F2LAnalyzer.isF2LSolved(cube.cubeState(), cube.orientation())
                && OLLAnalyzer.isOllSolved(cube.cubeState(), cube.orientation())
                && PLLAnalyzer.isPllSolved(cube.cubeState(), cube.orientation());
    }
}
