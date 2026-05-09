package test;

import cfop.OLLAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OLLAnalyzerTest {
    @Test
    void isOllSolved_shouldBeTrueForSolvedCube() {
        assertTrue(OLLAnalyzer.isOllSolved(new CubeState()));
    }

    @Test
    void isOllSolved_shouldBeFalseForKnownOllCase() {
        var cube = new CubeState();
        MoveApplier.executeAlgorithm(cube, "R U2 R' U' R U' R'");

        assertFalse(OLLAnalyzer.isOllSolved(cube));
    }

    @Test
    void extractSignature_shouldDistinguishCase22AndCase50Patterns() {
        var case22 = setupStateFor("R U2 R2 U' R2 U' R2 U2 R");
        var case50 = setupStateFor("r' U r2 U' r2 U' r2 U r'");

        assertNotEquals(
                OLLAnalyzer.extractSignature(case22.cubeState(), case22.orientation()),
                OLLAnalyzer.extractSignature(case50.cubeState(), case50.orientation())
        );
    }

    @Test
    void extractSignature_shouldDistinguishDotAndLinePatterns() {
        var case3 = setupStateFor("r' R2 U R' U r U2 r' U M'");
        var case13 = setupStateFor("F U R U' R2 F' R U R U' R'");

        assertNotEquals(
                OLLAnalyzer.extractSignature(case3.cubeState(), case3.orientation()),
                OLLAnalyzer.extractSignature(case13.cubeState(), case13.orientation())
        );
    }

    @Test
    void extractSignature_shouldIgnoreLastLayerPermutationWhenOrientationMatches() {
        var solved = new CubeState();
        var permuted = new CubeState();

        MoveApplier.executeAlgorithm(permuted, "R U R' U' R' F R2 U' R' U' R U R' F'");

        assertEquals(
                OLLAnalyzer.extractSignature(solved),
                OLLAnalyzer.extractSignature(permuted)
        );
    }

    private static OrientedCube setupStateFor(String algorithm) {
        var orientedCube = new OrientedCube();
        orientedCube.applyMoves(Algorithm.parse(algorithm).inverse().getMoves());
        return orientedCube;
    }
}
