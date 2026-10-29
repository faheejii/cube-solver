package test.solver;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.F2LSlot;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.CrossQualityAnalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossQualityAnalyzerTest {
    @Test
    void countsEverySolvedPairCountOnlyWhenCrossIsSolved() {
        for (int pairCount = 0; pairCount <= 4; pairCount++) {
            var cube = new CubeState();
            int unsolvedPairs = 4 - pairCount;
            for (int i = 0; i < unsolvedPairs; i++) {
                var slot = F2LSlot.values()[i];
                cube.cornerOri[slot.corner().ordinal()] = 1;
                cube.edgeOri[slot.edge().ordinal()] = 1;
            }
            cube.cornerOri[0] = (byte) ((3 - unsolvedPairs % 3) % 3);
            if (unsolvedPairs % 2 != 0) {
                cube.edgeOri[0] = 1;
            }

            var postCross = new OrientedCube(cube);
            assertTrue(CrossAnalyzer.isCrossSolved(cube, postCross.orientation()));
            assertEquals(pairCount, F2LAnalyzer.countSolvedSlots(cube, postCross.orientation()));
            assertEquals(pairCount, CrossQualityAnalyzer.solvedF2lPairCount(postCross));
        }
    }

    @Test
    void returnsUnavailableWhenCrossIsNotSolved() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "F");

        assertNull(CrossQualityAnalyzer.solvedF2lPairCount(new OrientedCube(cube)));
    }

    @Test
    void replayUsesThePostCrossOrientationAndMatchesTheLiveResult() {
        var scramble = "R D R' D2 R D' R'";
        var result = new solver.CfopSolveService().solve(new solver.CfopSolveRequest(scramble, Face.U));
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);
        var postCross = new OrientedCube(cube);
        postCross.applyAlgorithm(result.cross().algorithm());

        assertEquals(result.cross().solvedF2lPairCount(),
                CrossQualityAnalyzer.solvedF2lPairCount(scramble, result.cross().algorithm()));
        assertEquals(result.cross().solvedF2lPairCount(),
                CrossQualityAnalyzer.solvedF2lPairCount(postCross));
    }
}
