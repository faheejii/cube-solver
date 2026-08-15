package test;

import algorithms.AlgorithmCaseCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class F2LCaseCatalogTest {
    @Test
    void canonicalResources_shouldLoadAndDeriveIndexedCases() {
        var setup = AlgorithmCaseCatalog.setupDatabase();
        var insert = AlgorithmCaseCatalog.insertDatabase();
        var oll = AlgorithmCaseCatalog.ollDatabase();
        var pll = AlgorithmCaseCatalog.pllDatabase();

        assertEquals(106, setup.size());
        assertEquals(14, insert.size());
        assertEquals(57, oll.size());
        assertEquals(21, pll.size());
        assertTrue(AlgorithmCaseCatalog.entries(false).stream()
                .allMatch(entry -> "canonical".equals(entry.status())));
        assertFalse(AlgorithmCaseCatalog.entries(false).stream()
                .anyMatch(entry -> entry.name().startsWith("regression-")));
    }

    @Test
    void catalogEntries_shouldExposeDerivedSignaturesAndSourceSetups() {
        var entries = AlgorithmCaseCatalog.entries(false);

        assertTrue(entries.stream().anyMatch(entry ->
                "setup".equals(entry.phase()) && entry.sourceSetup() != null && entry.signature() != null));
        assertTrue(entries.stream().anyMatch(entry ->
                "insert".equals(entry.phase()) && entry.sourceSetup() == null && entry.signature() != null));
        assertTrue(entries.stream().anyMatch(entry ->
                "oll".equals(entry.phase()) && entry.previewSetup() != null && entry.signature() instanceof cfop.OLLCaseSignature));
        assertTrue(entries.stream().anyMatch(entry ->
                "pll".equals(entry.phase()) && entry.previewSetup() != null && entry.signature() instanceof cfop.PLLCaseSignature));
    }
}
