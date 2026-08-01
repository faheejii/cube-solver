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
    void solve_shouldUseCompiledExecutableNotationForSeededCase20() {
        var database = OLLCaseDatabase.seedCases();
        var algorithm = database.allCases().stream()
                .filter(ollCase -> ollCase.name().equals("case-20"))
                .findFirst()
                .orElseThrow()
                .algorithm();

        var setup = setupCubeFor(CubeOrientationKey.all().get(0), algorithm);
        setup.applyMoves(algorithm.getMoves());

        assertEquals("L F R' F' L' R L' R B R B' R' B' R' L", algorithm.toString());
        assertTrue(algorithm.getMoves().stream()
                .noneMatch(move -> move.isCubeRotation() || move.isWideMove() || move.ordinal() / 3 >= 12));
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
    void solve_shouldRecognizePostF2lOllAfterCrossAndSlotFrameRotations() {
        var cube = new OrientedCube();
        cube.applyAlgorithm("B2 U L D2 R' D2 F' L2 F U F2 D' L2 U2 D' F2 R2 L2 D' L2 U2");
        cube.applyAlgorithm("x' D2 R F R2");
        cube.applyAlgorithm("U2 L' U' L U2 F U' F' U2 R U R' y R' U R U2 "
                + "F' R U R' U' R' F R U' F U' R U' R' F' U' L' U L");

        assertTrue(CrossAnalyzer.isCrossSolved(cube.cubeState(), cube.orientation()));
        assertTrue(F2LAnalyzer.isF2LSolved(cube.cubeState(), cube.orientation()));

        var solution = new OLLSolver(OLLCaseDatabase.seedCases()).solve(cube);
        cube.applyMoves(solution.getMoves());

        assertTrue(solution.getMoves().stream().noneMatch(Move::isCubeRotation));
        assertTrue(OLLAnalyzer.isOllSolved(cube.cubeState(), cube.orientation()));
    }

    @Test
    void seededCases_shouldReplayAcrossAllFramesAndAufsWithoutCubeRotations() {
        var database = OLLCaseDatabase.seedCases();
        var solver = new OLLSolver(database);
        for (var orientationKey : CubeOrientationKey.all()) {
            var solved = new OrientedCube(new CubeState(), orientationKey.toOrientation());
            assertTrue(CrossAnalyzer.isCrossSolved(solved.cubeState(), solved.orientation()),
                    "solved cross frame=" + orientationKey);
            assertTrue(F2LAnalyzer.isF2LSolved(solved.cubeState(), solved.orientation()),
                    "solved F2L frame=" + orientationKey);
        }
        for (var ollCase : database.allCases()) {
            for (var orientationKey : CubeOrientationKey.all()) {
                for (var auf : AUF_TRIALS) {
                    var setupAlgorithm = Algorithm.normalize(auf.concat(ollCase.algorithm()));
                    var orientedCube = setupCubeFor(orientationKey, setupAlgorithm);
                    assertTrue(CrossAnalyzer.isCrossSolved(orientedCube.cubeState(), orientedCube.orientation()),
                            ollCase.name() + " setup cross=" + CrossAnalyzer.countSolvedCrossEdges(orientedCube.cubeState(), orientedCube.orientation())
                                    + " frame=" + orientationKey + " actual=" + orientedCube.orientation() + " auf=" + auf);
                    assertTrue(F2LAnalyzer.isF2LSolved(orientedCube.cubeState(), orientedCube.orientation()),
                            ollCase.name() + " setup F2L frame=" + orientationKey + " auf=" + auf);
                    Algorithm solution;
                    try {
                        solution = solver.solve(orientedCube);
                    } catch (RuntimeException exception) {
                        throw new AssertionError(
                                ollCase.name() + " frame=" + orientationKey + " auf=" + auf,
                                exception
                        );
                    }
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
        var setup = new OrientedCube(new CubeState(), setupOrientationKey.toOrientation());
        setup.applyMoves(algorithm.inverse().getMoves());
        return setup;
    }
}
