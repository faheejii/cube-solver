package test;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.CfopSolveRequest;
import solver.CfopSolveService;
import solver.F2LComparisonCode;
import solver.F2LMode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfopSolveServiceTest {
    private static final CfopSolveService SERVICE = new CfopSolveService();

    @Test
    void solve_shouldReturnStructuredTwoPhaseResult() {
        var result = SERVICE.solve(new CfopSolveRequest("R D R' D2 R D' R'", cube.Face.U));

        assertEquals("z2", result.cross().algorithm());
        assertTrue(result.f2lSetupCaseCount() > 0);
        assertTrue(result.f2lInsertCaseCount() > 0);
        assertTrue(result.cross().solved());
        assertTrue(result.f2l().solved());
        assertTrue(result.fullySolved());
    }

    @Test
    void solve_withOptimizedF2LMode_shouldReturnSolvedResultNoLongerThanFastCfop() {
        var scramble = "R D R' D2 R D' R'";

        var greedy = SERVICE.solve(new CfopSolveRequest(scramble, cube.Face.U, F2LMode.GREEDY));
        var optimized = SERVICE.solve(new CfopSolveRequest(scramble, cube.Face.U, F2LMode.OPTIMIZED));

        assertEquals("optimized", optimized.f2lMode());
        assertTrue(optimized.f2l().solved());
        assertTrue(optimized.fullySolved());
        assertTrue(totalCfopMoves(optimized) <= totalCfopMoves(greedy));
        assertTrue(optimized.modeComparison() != null);
        assertEquals(optimized.crossFace(), optimized.modeComparison().fast().crossFace());
        assertEquals(optimized.crossFace(), optimized.modeComparison().optimized().crossFace());
        assertEquals(
                totalCfopMoves(optimized) - totalCfopMoves(greedy),
                optimized.modeComparison().totalMoveDifference()
        );
        assertTrue(optimized.modeComparison().explanationCodes().stream()
                .allMatch(code -> code != F2LComparisonCode.LOCAL_PAIR_LONGER_GLOBAL_ROUTE_SHORTER
                        || optimized.modeComparison().f2lMoveDifference() > 0));
    }

    @Test
    void solve_withColorNeutralCross_shouldReturnChosenConcreteFace() {
        var service = new CfopSolveService();
        var result = service.solve(CfopSolveRequest.colorNeutral("R D R' D2 R D' R'"));

        assertTrue(java.util.Set.of("U", "D", "F", "B", "L", "R").contains(result.crossFace()));
        assertTrue(result.cross().solved());
        assertTrue(result.f2l().solved());
        assertTrue(result.fullySolved());
        assertEquals(0, service.f2lDiagnostics().totalDatabaseMisses());
    }

    @Test
    void solve_withOptimizedColorNeutral_shouldKeepFrameConsistentThroughOll() {
        var scramble = "B2 U L D2 R' D2 F' L2 F U F2 D' L2 U2 D' F2 R2 L2 D' L2 U2";

        var result = SERVICE.solve(CfopSolveRequest.colorNeutral(scramble, F2LMode.OPTIMIZED));

        assertTrue(result.cross().solved());
        assertTrue(result.f2l().solved());
        assertTrue(result.oll().solved(), result.oll().status());
        assertTrue(result.pll().solved(), result.pll().status());
        assertTrue(result.fullySolved());

        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);
        var replay = new OrientedCube(cube);

        replay.applyAlgorithm(result.cross().algorithm());
        assertTrue(CrossAnalyzer.isCrossSolved(replay.cubeState(), replay.orientation()));

        replay.applyAlgorithm(result.f2l().algorithm());
        assertTrue(F2LAnalyzer.isF2LSolved(replay.cubeState(), replay.orientation()));

        replay.applyAlgorithm(result.oll().algorithm());
        assertTrue(result.oll().algorithm().isBlank()
                || Algorithm.parse(result.oll().algorithm()).getMoves().stream().noneMatch(Move::isCubeRotation));
        assertTrue(OLLAnalyzer.isOllSolved(replay.cubeState(), replay.orientation()));

        replay.applyAlgorithm(result.pll().algorithm());
        assertTrue(result.pll().algorithm().isBlank()
                || Algorithm.parse(result.pll().algorithm()).getMoves().stream().noneMatch(Move::isCubeRotation));
        assertTrue(PLLAnalyzer.isPllSolved(replay.cubeState(), replay.orientation()));
        assertTrue(result.fullySolved());
        assertTrue(fullySolved(replay));

        var combined = String.join(" ",
                result.cross().algorithm(),
                result.f2l().algorithm(),
                result.oll().algorithm(),
                result.pll().algorithm()
        ).trim().replaceAll("\\s+", " ");
        var combinedReplayCube = new CubeState();
        MoveApplier.applyAlgorithm(combinedReplayCube, scramble);
        var combinedReplay = new OrientedCube(combinedReplayCube);
        combinedReplay.applyAlgorithm(combined);
        assertTrue(fullySolved(combinedReplay));
    }

    @Test
    void solve_regressionScrambleWithRcross_shouldNotCycleInGreedyF2l() {
        var scramble = "B R' F U D' R D' R2 B U2 R U2 L' D2 R F2 R2 D2 R B2 R2";
        var service = new CfopSolveService();

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(15),
                () -> service.solve(new CfopSolveRequest(scramble, cube.Face.R, F2LMode.GREEDY))
        );

        assertTrue(result.f2l().solved());
        assertTrue(result.fullySolved());
        assertEquals(0, service.f2lDiagnostics().totalDatabaseMisses());
    }

    @Test
    void solve_regressionScrambleColorNeutralOptimized_shouldUseExactlyThreeShortlistedColors() {
        var scramble = "B R' F U D' R D' R2 B U2 R U2 L' D2 R F2 R2 D2 R B2 R2";
        var shortlistSize = new AtomicInteger();
        var service = new CfopSolveService();

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(35),
                () -> service.solveWithProgress(
                        CfopSolveRequest.colorNeutral(scramble, F2LMode.OPTIMIZED),
                        progress -> {
                            if (progress.phase() == solver.F2LSolver.SolvePhase.BASELINE_COMPARISON) {
                                shortlistSize.set(progress.totalCrosses());
                            }
                        }
                )
        );

        assertEquals(3, shortlistSize.get());
        assertTrue(result.fullySolved());
        assertEquals(0, service.f2lDiagnostics().totalDatabaseMisses());
    }

    private static int totalCfopMoves(solver.CfopSolveResult result) {
        return result.cross().moveCount()
                + result.f2l().moveCount()
                + result.oll().moveCount()
                + result.pll().moveCount();
    }

    private static boolean fullySolved(OrientedCube cube) {
        return CrossAnalyzer.isCrossSolved(cube.cubeState(), cube.orientation())
                && F2LAnalyzer.isF2LSolved(cube.cubeState(), cube.orientation())
                && OLLAnalyzer.isOllSolved(cube.cubeState(), cube.orientation())
                && PLLAnalyzer.isPllSolved(cube.cubeState(), cube.orientation());
    }
}
