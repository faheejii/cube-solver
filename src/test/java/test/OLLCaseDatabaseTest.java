package test;

import algorithms.OLLCaseDatabase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OLLCaseDatabaseTest {
    @Test
    void seedCases_shouldNotContainSignatureCollisions() {
        assertTrue(OLLCaseDatabase.duplicateSeedCases().isEmpty());
    }
}
