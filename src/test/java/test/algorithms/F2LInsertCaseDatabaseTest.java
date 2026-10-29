package test.algorithms;

import algorithms.F2LInsertCase;
import algorithms.F2LInsertCaseDatabase;
import cfop.F2LSlot;
import cube.Algorithm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class F2LInsertCaseDatabaseTest {
    @Test
    void register_shouldRetainDistinctNamedAlternativesForTheSameCase() {
        var database = F2LInsertCaseDatabase.empty();
        database.register("R U R'", F2LSlot.FR, "right-insert-short");
        var first = database.allCases().iterator().next();
        database.register(new F2LInsertCase(
                first.insertSlot(),
                first.preservedSlots(),
                first.signature(),
                Algorithm.parse("R U R' U U'"),
                "right-insert-alternative"
        ));

        database.validate();
        assertEquals(2, database.allCases().size());
        assertEquals(2, database.findCompatible(
                first.insertSlot(), first.preservedSlots(), first.signature()).size());
    }

    @Test
    void register_shouldRejectReusingAnAlgorithmName() {
        var database = F2LInsertCaseDatabase.empty();
        database.register("R U R'", F2LSlot.FR, "right-insert");

        assertThrows(IllegalArgumentException.class,
                () -> database.register("R U' R'", F2LSlot.FR, "right-insert"));
    }
}
