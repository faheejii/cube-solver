package test.algorithms;

import algorithms.AlgorithmCaseCatalog;
import algorithms.AlgorithmDatabaseFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlgorithmDatabaseFactoryTest {
    @Test
    void build_shouldRecreateAllSeedPhaseIndexes() {
        var definitions = AlgorithmCaseCatalog.entries(true);
        var databases = AlgorithmDatabaseFactory.build(definitions);

        assertEquals(AlgorithmCaseCatalog.setupDatabase().size(), databases.setup().size());
        assertEquals(AlgorithmCaseCatalog.insertDatabase().size(), databases.insert().size());
        assertEquals(AlgorithmCaseCatalog.ollDatabase().size(), databases.oll().size());
        assertEquals(AlgorithmCaseCatalog.pllDatabase().size(), databases.pll().size());
    }

    @Test
    void build_shouldPreserveDistinctAlternativesWithTheSameF2LInsertSignature() {
        var definitions = new ArrayList<>(AlgorithmCaseCatalog.entries(true));
        var seed = definitions.stream().filter(entry -> "insert".equals(entry.phase())).findFirst().orElseThrow();
        definitions.add(new AlgorithmCaseCatalog.CatalogEntry(
                seed.phase(), seed.name() + "-alternative", seed.slot(), seed.nonPreservedSlot(),
                seed.preservedSlots(), seed.signature(), seed.algorithm() + " U U'", seed.sourceSetup(),
                seed.previewSetup(), seed.status(), seed.notes()
        ));

        var databases = AlgorithmDatabaseFactory.build(definitions);
        assertEquals(AlgorithmCaseCatalog.insertDatabase().size() + 1, databases.insert().size());
    }

    @Test
    void build_shouldRejectUnknownPhasesAndNullInput() {
        assertThrows(IllegalArgumentException.class, () -> AlgorithmDatabaseFactory.build(null));
        var definition = AlgorithmCaseCatalog.entries(true).getFirst();
        var invalid = new AlgorithmCaseCatalog.CatalogEntry("unknown", definition.name(), definition.slot(),
                definition.nonPreservedSlot(), definition.preservedSlots(), definition.signature(),
                definition.algorithm(), definition.sourceSetup(), definition.previewSetup(),
                definition.status(), definition.notes());
        assertThrows(IllegalArgumentException.class, () -> AlgorithmDatabaseFactory.build(java.util.List.of(invalid)));
    }
}
