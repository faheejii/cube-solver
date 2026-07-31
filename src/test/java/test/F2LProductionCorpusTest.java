package test;

import cube.Face;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import solver.CfopSolveRequest;
import solver.CfopSolveService;
import solver.F2LMode;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class F2LProductionCorpusTest {
    private static final List<CorpusCase> FIXED_FACE_CASES = List.of(
            new CorpusCase(Face.U, "R D R' D2 R D' R'"),
            new CorpusCase(Face.D, "D2 F2 D' L F R F' B' D' U2 F2 L2 B R2 F2 R2 D2 R2 B D2 R2"),
            new CorpusCase(Face.F, "B2 U L D2 R' D2 F' L2 F U F2 D' L2 U2 D' F2 R2 L2 D' L2 U2"),
            new CorpusCase(Face.B, "R U2 F' L2 D B2 R' U F2 D' L U2 B R2 D2 F L2 U'"),
            new CorpusCase(Face.L, "F2 R2 U' B2 L D2 F' U R B' D L2 U2 F2 R'"),
            new CorpusCase(Face.R, "B R' F U D' R D' R2 B U2 R U2 L' D2 R F2 R2 D2 R B2 R2")
    );

    @Test
    @EnabledIfSystemProperty(named = "f2l.corpus", matches = "true")
    void productionDatabase_shouldCoverFixedFacesAndOptimizationModesWithoutIdaStar() {
        var service = new CfopSolveService();

        for (var corpusCase : FIXED_FACE_CASES) {
            var result = assertTimeoutPreemptively(
                    Duration.ofSeconds(20),
                    () -> service.solve(new CfopSolveRequest(
                            corpusCase.scramble(),
                            corpusCase.face(),
                            F2LMode.GREEDY
                    )),
                    corpusCase.toString()
            );
            assertTrue(result.fullySolved(), corpusCase + " result=" + result);
        }

        for (var corpusCase : FIXED_FACE_CASES.subList(0, 2)) {
            var result = assertTimeoutPreemptively(
                    Duration.ofSeconds(35),
                    () -> service.solve(new CfopSolveRequest(
                            corpusCase.scramble(),
                            corpusCase.face(),
                            F2LMode.OPTIMIZED
                    )),
                    "optimized " + corpusCase
            );
            assertTrue(result.fullySolved(), "optimized " + corpusCase);
        }

        var colorNeutral = assertTimeoutPreemptively(
                Duration.ofSeconds(35),
                () -> service.solve(CfopSolveRequest.colorNeutral(
                        FIXED_FACE_CASES.get(2).scramble(),
                        F2LMode.OPTIMIZED
                ))
        );
        assertTrue(colorNeutral.fullySolved());
        assertEquals(0, service.f2lDiagnostics().totalDatabaseMisses());
    }

    private record CorpusCase(Face face, String scramble) {
    }
}
