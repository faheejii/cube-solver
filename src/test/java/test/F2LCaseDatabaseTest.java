package test;

import algorithms.F2LCase;
import algorithms.F2LCaseDatabase;
import cfop.F2LCaseSignature;
import cube.Algorithm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class F2LCaseDatabaseTest {
    @Test
    void normalizeBySignatureKeepingFirst_shouldKeepOnlyFirstCaseForDuplicateSignature() {
        var signature = new F2LCaseSignature(null, 0, null, 0);
        var first = new F2LCase(signature, Algorithm.parse("R U R'"), "first");
        var duplicate = new F2LCase(signature, Algorithm.parse("R U' R'"), "duplicate");

        var database = F2LCaseDatabase.normalizeBySignatureKeepingFirst(List.of(first, duplicate));

        assertEquals(1, database.size());
        assertEquals("first", database.find(signature).orElseThrow().name());
    }

    @Test
    void duplicateSeedBasicCases_shouldExposeSeedCollisions() {
        assertFalse(F2LCaseDatabase.duplicateSeedBasicCases().isEmpty());
    }
}
