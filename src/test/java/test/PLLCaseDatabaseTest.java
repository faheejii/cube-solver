package test;

import algorithms.PLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PLLCaseDatabaseTest {
    @Test
    void seedCases_shouldNotContainSignatureCollisions() {
        assertTrue(PLLCaseDatabase.duplicateSeedCases().isEmpty());
    }

    @Test
    void seedCases_shouldOnlyContainAlgorithmsThatPreservePllPreconditionsAndSolve() {
        var database = PLLCaseDatabase.seedCases();

        assertEquals(84, database.size());
        for (var pllCase : database.allCases()) {
            var cube = new OrientedCube();
            cube.applyMoves(pllCase.algorithm().inverse().getMoves());

            assertTrue(CrossAnalyzer.isCrossSolved(cube.cubeState(), cube.orientation()), pllCase.name());
            assertTrue(F2LAnalyzer.isF2LSolved(cube.cubeState(), cube.orientation()), pllCase.name());
            assertTrue(OLLAnalyzer.isOllSolved(cube.cubeState(), cube.orientation()), pllCase.name());

            cube.applyMoves(pllCase.algorithm().getMoves());

            assertTrue(PLLAnalyzer.isPllSolved(cube.cubeState(), cube.orientation()), pllCase.name());
        }
    }
}
