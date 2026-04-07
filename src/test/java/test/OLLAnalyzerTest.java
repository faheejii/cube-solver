package test;

import cfop.OLLAnalyzer;
import cube.CubeState;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OLLAnalyzerTest {
    @Test
    void isOllSolved_shouldBeTrueForSolvedCube() {
        assertTrue(OLLAnalyzer.isOllSolved(new CubeState()));
    }

    @Test
    void isOllSolved_shouldBeFalseForKnownOllCase() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U2 R' U' R U' R'");

        assertFalse(OLLAnalyzer.isOllSolved(cube));
    }
}
