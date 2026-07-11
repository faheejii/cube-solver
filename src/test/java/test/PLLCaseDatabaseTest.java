package test;

import algorithms.PLLCaseDatabase;
import cfop.PLLAnalyzer;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class PLLCaseDatabaseTest {
    @Test
    void seedCases_shouldReportLogicalCasesSeparatelyFromFrameAndAufVariants() {
        var database = PLLCaseDatabase.seedCases();

        assertEquals(21, database.size());
        assertEquals(21 * 24 * 4, database.frameVariantCount());
    }

    @Test
    void findAll_shouldReturnTheSeededCaseForItsExactFrameAwareSignature() {
        var database = PLLCaseDatabase.seedCases();
        var pllCase = database.allCases().stream().findFirst().orElseThrow();
        var setup = setupCubeFor(CubeOrientationKey.all().get(0), pllCase.algorithm());
        var signature = PLLAnalyzer.extractSignature(setup.cubeState(), setup.orientation());

        assertFalse(database.findAll(CubeOrientationKey.from(setup.orientation()), signature).isEmpty());
    }

    private static OrientedCube setupCubeFor(CubeOrientationKey setupOrientationKey, cube.Algorithm algorithm) {
        var solvedOrientation = new OrientedCube(new CubeState(), setupOrientationKey.toOrientation());
        solvedOrientation.applyMoves(algorithm.getMoves());
        var setup = new OrientedCube(new CubeState(), solvedOrientation.orientation());
        setup.applyMoves(algorithm.inverse().getMoves());
        return setup;
    }
}
