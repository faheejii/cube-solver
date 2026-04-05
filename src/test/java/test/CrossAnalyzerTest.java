package test;

import cfop.CrossAnalyzer;
import cube.CubeState;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrossAnalyzerTest {
    @Test
    void solvedCube_shouldHaveSolvedDCross() {
        var cube = new CubeState();

        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertEquals(4, CrossAnalyzer.countSolvedCrossEdges(cube));
    }

    @Test
    void scrambledCube_shouldReportDCrossAsUnsolved() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "F R U");

        assertFalse(CrossAnalyzer.isCrossSolved(cube));
        assertTrue(CrossAnalyzer.countSolvedCrossEdges(cube) < 4);
    }
}
