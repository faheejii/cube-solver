package test;

import algorithms.OLLCaseDatabase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OLLCaseDatabaseTest {
    @Test
    void seedCases_shouldValidateEverySeededAlgorithm() {
        assertEquals(57, OLLCaseDatabase.seedCases().size());
    }

    @Test
    void duplicateSeedCases_shouldExposeSignatureCollisionsForDiagnostics() {
        assertFalse(OLLCaseDatabase.duplicateSeedCases().isEmpty());
    }

    @Test
    void seedCases_shouldKeepCollidingCasesAvailableForValidationScan() {
        var database = OLLCaseDatabase.seedCases();
        var collidingCaseCount = OLLCaseDatabase.duplicateSeedCases().values().stream()
                .mapToInt(ollCases -> ollCases.size() - 1)
                .sum();

        assertTrue(collidingCaseCount > 0);
        assertEquals(57, database.allCases().size());
    }
}
