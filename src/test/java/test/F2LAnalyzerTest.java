package test;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.F2LSlot;
import cube.CubeState;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LAnalyzerTest {
    @Test
    void solvedCube_shouldHaveSolvedF2L() {
        var cube = new CubeState();

        assertTrue(F2LAnalyzer.isF2LSolved(cube));
        assertEquals(4, F2LAnalyzer.countSolvedSlots(cube));
        assertTrue(F2LAnalyzer.getUnsolvedSlots(cube).isEmpty());
    }

    @Test
    void simpleF2LBreak_shouldKeepCrossButUnsovleFrontRightSlot() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R'");

        assertTrue(CrossAnalyzer.isCrossSolved(cube));
        assertFalse(F2LAnalyzer.isF2LSolved(cube));
        assertFalse(F2LAnalyzer.isSlotSolved(cube, F2LSlot.FR));
        assertTrue(F2LAnalyzer.countSolvedSlots(cube) < 4);
        assertTrue(F2LAnalyzer.getUnsolvedSlots(cube).contains(F2LSlot.FR));
    }
}
