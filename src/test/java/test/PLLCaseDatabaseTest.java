package test;

import algorithms.PLLCaseDatabase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PLLCaseDatabaseTest {
    @Test
    void seedCases_shouldNotContainSignatureCollisions() {
        assertTrue(PLLCaseDatabase.duplicateSeedCases().isEmpty());
    }

    @Test
    void seedCases_shouldValidateEveryFinalAufSeededSetup() {
        var database = PLLCaseDatabase.seedCases();

        assertEquals(84, database.size());
    }
}
