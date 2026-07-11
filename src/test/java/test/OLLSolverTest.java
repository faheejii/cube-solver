package test;

import algorithms.OLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cube.Algorithm;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.OLLSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OLLSolverTest {
    private static final Algorithm[] AUF_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(java.util.List.of(Move.U)),
            Algorithm.fromMoves(java.util.List.of(Move.U2)),
            Algorithm.fromMoves(java.util.List.of(Move.U_PRIME))
    };

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

        var setup = setupCubeFor(CubeOrientationKey.all().get(0), algorithm);
        setup.applyMoves(algorithm.getMoves());

        assertEquals("r U R' U' M2 U R U' R' U' M'", algorithm.toString());
        assertTrue(CrossAnalyzer.isCrossSolved(setup.cubeState(), setup.orientation()));
        assertTrue(F2LAnalyzer.isF2LSolved(setup.cubeState(), setup.orientation()));
        assertTrue(OLLAnalyzer.isOllSolved(setup.cubeState(), setup.orientation()));
    }

    @Test
    void solve_shouldTryAufBeforeLookup() {
        var orientedCube = new OrientedCube();
        var algorithm = "R U R' U R U2 R'";
        orientedCube.applyMoves(Algorithm.parse(algorithm).inverse().getMoves());
        orientedCube.applyAlgorithm("U'");

        var database = OLLCaseDatabase.empty();
        database.register(algorithm, "sune");
        var solution = new OLLSolver(database).solve(orientedCube);
        orientedCube.applyMoves(solution.getMoves());

        assertTrue(CrossAnalyzer.isCrossSolved(orientedCube.cubeState(), orientedCube.orientation()));
        assertTrue(F2LAnalyzer.isF2LSolved(orientedCube.cubeState(), orientedCube.orientation()));
        assertTrue(OLLAnalyzer.isOllSolved(orientedCube.cubeState(), orientedCube.orientation()));
    }

    @Test
    void solve_shouldCoverEverySeededCaseAcrossAllFramesAndAufsWithoutCubeRotations() {
        var database = OLLCaseDatabase.seedCases();

        for (var ollCase : database.allCases()) {
            for (var orientationKey : CubeOrientationKey.all()) {
                for (var auf : AUF_TRIALS) {
                    var solution = Algorithm.normalize(auf.concat(ollCase.algorithm()));
                    var orientedCube = setupCubeFor(orientationKey, solution);
                    orientedCube.applyMoves(solution.getMoves());

                    assertTrue(solution.getMoves().stream().noneMatch(Move::isCubeRotation), ollCase.name());
                    assertTrue(CrossAnalyzer.isCrossSolved(orientedCube.cubeState(), orientedCube.orientation()), ollCase.name());
                    assertTrue(F2LAnalyzer.isF2LSolved(orientedCube.cubeState(), orientedCube.orientation()), ollCase.name());
                    assertTrue(OLLAnalyzer.isOllSolved(orientedCube.cubeState(), orientedCube.orientation()), ollCase.name());
                }
            }
        }
    }

    private static OrientedCube setupCubeFor(CubeOrientationKey setupOrientationKey, Algorithm algorithm) {
        var solvedOrientation = new OrientedCube(new CubeState(), setupOrientationKey.toOrientation());
        solvedOrientation.applyMoves(algorithm.getMoves());
        var setup = new OrientedCube(new CubeState(), solvedOrientation.orientation());
        setup.applyMoves(algorithm.inverse().getMoves());
        return setup;
    }
}
