package test;

import algorithms.OLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cube.CubeState;
import cube.Algorithm;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.CrossSolver;
import solver.F2LSolver;
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
        MoveApplier.executeMoves(cube, Algorithm.parse(algorithm).inverse().getMoves());

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

    @Test
    void solve_shouldUsePhysicalNotationForSeededCase20() {
        var database = OLLCaseDatabase.seedCases();
        var algorithm = database.allCases().stream()
                .filter(ollCase -> ollCase.name().equals("case-20"))
                .findFirst()
                .orElseThrow()
                .algorithm();

        var setup = new OrientedCube();
        setup.applyMoves(algorithm.inverse().getMoves());

        var solution = new OLLSolver(database).solve(setup);
        setup.applyMoves(solution.getMoves());

        assertEquals("r U R' U' M2 U R U' R' U' M'", algorithm.toString());
        assertTrue(CrossAnalyzer.isCrossSolved(setup.cubeState(), setup.orientation()));
        assertTrue(F2LAnalyzer.isF2LSolved(setup.cubeState(), setup.orientation()));
        assertTrue(OLLAnalyzer.isOllSolved(setup.cubeState(), setup.orientation()));
    }

    @Test
    void solve_shouldFindCaseAfterYPrefixForSolverMainScramble() {
        var orientedCube = new OrientedCube();
        orientedCube.applyAlgorithm("F L2 D' R B2 D2 F' U B2 U2 F2 D2 R2 U2 R' B2 R' B2 R' U' R2");
        orientedCube.applyMoves(new CrossSolver().solve(orientedCube.cubeState(), cube.Face.U).getMoves());
        orientedCube.applyMoves(new F2LSolver().solve(orientedCube).getMoves());

        var solution = new OLLSolver(OLLCaseDatabase.seedCases()).solve(orientedCube);
        orientedCube.applyMoves(solution.getMoves());

        assertTrue(CrossAnalyzer.isCrossSolved(orientedCube.cubeState(), orientedCube.orientation()));
        assertTrue(F2LAnalyzer.isF2LSolved(orientedCube.cubeState(), orientedCube.orientation()));
        assertTrue(OLLAnalyzer.isOllSolved(orientedCube.cubeState(), orientedCube.orientation()));
    }

    @Test
    void solve_shouldFindCase2AfterAufInBFaceFrame() {
        var orientedCube = new OrientedCube();
        orientedCube.applyAlgorithm("D2 F2 D' L F R F' B' D' U2 F2 L2 B R2 F2 R2 D2 R2 B D2 R2");
        orientedCube.applyAlgorithm("x y2 L F' L2 D' R");
        orientedCube.applyAlgorithm(
                "U F' U F U2 F U' F' y2 R U' R' U' R U' R2 F R F' "
                        + "y2 F' U F U2 R' U2 R"
        );

        var database = OLLCaseDatabase.seedCases();
        var solution = new OLLSolver(database).solve(orientedCube);
        orientedCube.applyMoves(solution.getMoves());

        assertTrue(solution.toString().contains("r U r'"));
        assertTrue(solution.getMoves().contains(Move.RW));
        assertTrue(CrossAnalyzer.isCrossSolved(orientedCube.cubeState(), orientedCube.orientation()));
        assertTrue(F2LAnalyzer.isF2LSolved(orientedCube.cubeState(), orientedCube.orientation()));
        assertTrue(OLLAnalyzer.isOllSolved(orientedCube.cubeState(), orientedCube.orientation()));
    }
}
