package test;

import algorithms.F2LCase;
import algorithms.F2LCaseDatabase;
import cfop.F2LCaseSignature;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.Corner;
import cube.Edge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class F2LCaseDatabaseTest {
    @Test
    void normalizeBySignatureKeepingFirst_shouldKeepOnlyFirstCaseForDuplicateSignature() {
        var signature = new F2LCaseSignature(null, 0, null, 0);
        var first = new F2LCase(F2LSlot.FR, signature, Algorithm.parse("R U R'"), "first");
        var duplicate = new F2LCase(F2LSlot.FR, signature, Algorithm.parse("R U' R'"), "duplicate");

        var database = F2LCaseDatabase.normalizeBySignatureKeepingFirst(List.of(first, duplicate));

        assertEquals(1, database.size());
        assertEquals("first", database.find(F2LSlot.FR, signature).orElseThrow().name());
    }

    @Test
    void normalizeBySignatureKeepingFirst_shouldKeepSameSignatureForDifferentSlots() {
        var signature = new F2LCaseSignature(Corner.URF, 0, Edge.UR, 0);
        var fr = new F2LCase(F2LSlot.FR, signature, Algorithm.parse("R U R'"), "fr");
        var fl = new F2LCase(F2LSlot.FL, signature, Algorithm.parse("L' U' L"), "fl");

        var database = F2LCaseDatabase.normalizeBySignatureKeepingFirst(List.of(fr, fl));

        assertEquals(2, database.size());
        assertEquals("fr", database.find(F2LSlot.FR, signature).orElseThrow().name());
        assertEquals("fl", database.find(F2LSlot.FL, signature).orElseThrow().name());
    }

    @Test
    void duplicateSeedBasicCases_shouldExposeSeedCollisionDiagnostics() {
        assertNotNull(F2LCaseDatabase.duplicateSeedBasicCases());
    }

    @Test
    void seedBasicCases_shouldAssignDefaultCaseNamesInOrder() {
        var database = F2LCaseDatabase.seedBasicCases();

        assertEquals("case-1", database.allCases().iterator().next().name());
    }
}
