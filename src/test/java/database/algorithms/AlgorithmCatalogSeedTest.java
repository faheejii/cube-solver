package database.algorithms;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlgorithmCatalogSeedTest {
    @Test
    void seedEntriesHaveStableUniqueIdentitiesAndPreserveSourceDefinitions() {
        var entries = AlgorithmCatalogSeed.entries();

        assertEquals(201, entries.size());
        assertEquals(entries.size(), new HashSet<>(entries.stream().map(AlgorithmCatalogSeed.SeedEntry::seedKey).toList()).size());
        assertEquals(entries.size(), new HashSet<>(entries.stream().map(AlgorithmCatalogSeed.SeedEntry::algorithmId).toList()).size());
        assertEquals(entries.size(), new HashSet<>(entries.stream().map(AlgorithmCatalogSeed.SeedEntry::revisionId).toList()).size());
        assertTrue(entries.stream().allMatch(entry ->
                entry.definitionJson().contains("\"algorithm\"")
                        && entry.definitionJson().contains("\"phase\"")
                        && entry.definitionJson().contains("\"status\":\"canonical\"")));

        var first = entries.getFirst();
        var repeated = AlgorithmCatalogSeed.entries().getFirst();
        assertEquals(first.seedKey(), repeated.seedKey());
        assertEquals(first.algorithmId(), repeated.algorithmId());
        assertEquals(first.revisionId(), repeated.revisionId());
        assertEquals(AlgorithmCatalogSeed.checksum(entries), AlgorithmCatalogSeed.checksum(AlgorithmCatalogSeed.entries()));
        assertNotEquals(first.revisionId(), AlgorithmCatalogSeed.revisionId(first.seedKey(), 2));
    }
}
