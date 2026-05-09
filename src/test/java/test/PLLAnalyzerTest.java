package test;

import cfop.PLLAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PLLAnalyzerTest {
    @Test
    void isPllSolved_shouldBeTrueForSolvedCube() {
        assertTrue(PLLAnalyzer.isPllSolved(new CubeState()));
    }

    @Test
    void isPllSolved_shouldBeFalseForKnownPllCase() {
        var cube = setupStateFor("R U R' U' R' F R2 U' R' U' R U R' F'");

        assertFalse(PLLAnalyzer.isPllSolved(cube.cubeState(), cube.orientation()));
    }

    @Test
    void extractSignature_shouldDistinguishDifferentPermutations() {
        var tPerm = setupStateFor("R U R' U' R' F R2 U' R' U' R U R' F'");
        var uPerm = setupStateFor("R U' R U R U R U' R' U' R2");

        assertNotEquals(
                PLLAnalyzer.extractSignature(tPerm.cubeState(), tPerm.orientation()),
                PLLAnalyzer.extractSignature(uPerm.cubeState(), uPerm.orientation())
        );
    }

    private static OrientedCube setupStateFor(String algorithm) {
        var orientedCube = new OrientedCube();
        orientedCube.applyMoves(Algorithm.parse(algorithm).inverse().getMoves());
        return orientedCube;
    }
}
