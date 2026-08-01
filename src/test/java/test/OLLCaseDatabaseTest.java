package test;

import algorithms.OLLCaseDatabase;
import cfop.OLLAnalyzer;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OLLCaseDatabaseTest {
    @Test
    void seedCases_shouldReportLogicalCasesSeparatelyFromFrameVariants() {
        var database = OLLCaseDatabase.seedCases();

        assertEquals(57, database.size());
        assertEquals(57 * 24, database.frameVariantCount());
    }

    @Test
    void duplicateSeedCases_shouldRemainAvailableForDiagnostics() {
        assertNotNull(OLLCaseDatabase.duplicateSeedCases());
    }

    @Test
    void seedCases_shouldKeepEveryLogicalCaseAvailableForLookup() {
        var database = OLLCaseDatabase.seedCases();
        assertEquals(57, database.allCases().size());
        for (var orientationKey : CubeOrientationKey.all()) {
            assertTrue(database.lookupSignatureCount(orientationKey) >= 57);
        }
    }

    @Test
    void findAll_shouldReturnTheSeededCaseForItsExactFrameAwareSignature() {
        var database = OLLCaseDatabase.seedCases();
        var ollCase = database.allCases().stream().findFirst().orElseThrow();
        var setup = setupCubeFor(CubeOrientationKey.all().get(0), ollCase.algorithm());
        var signature = OLLAnalyzer.extractSignature(setup.cubeState(), setup.orientation());
        var matches = database.findAll(CubeOrientationKey.from(setup.orientation()), signature);

        assertTrue(matches.size() >= 1);
        assertTrue(matches.stream().anyMatch(match -> match.name().startsWith(ollCase.name())));
    }

    @Test
    void findAll_shouldSeedLogicalSignaturesAsSeparateFrameEntries() {
        var database = OLLCaseDatabase.seedCases();
        var ollCase = database.allCases().stream().findFirst().orElseThrow();
        var sourceKey = CubeOrientationKey.all().get(0);
        var targetKey = CubeOrientationKey.all().get(1);
        var sourceSetup = setupCubeFor(sourceKey, ollCase.algorithm());
        var targetSetup = setupCubeFor(targetKey, ollCase.algorithm());
        var sourceSignature = OLLAnalyzer.extractSignature(sourceSetup.cubeState(), sourceSetup.orientation());
        var targetSignature = OLLAnalyzer.extractSignature(targetSetup.cubeState(), targetSetup.orientation());

        assertEquals(sourceSignature, targetSignature);
        assertTrue(database.findAll(sourceKey, sourceSignature).stream()
                .anyMatch(match -> match.name().startsWith(ollCase.name())));
        assertTrue(database.findAll(targetKey, targetSignature).stream()
                .anyMatch(match -> match.name().startsWith(ollCase.name())));
    }

    private static OrientedCube setupCubeFor(CubeOrientationKey setupOrientationKey, cube.Algorithm algorithm) {
        var solvedOrientation = new OrientedCube(new CubeState(), setupOrientationKey.toOrientation());
        solvedOrientation.applyMoves(algorithm.getMoves());
        var setup = new OrientedCube(new CubeState(), solvedOrientation.orientation());
        setup.applyMoves(algorithm.inverse().getMoves());
        return setup;
    }
}
