package database.algorithms;

import algorithms.AlgorithmCaseCatalog;
import algorithms.AlgorithmCaseCatalog.SourceDefinition;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/** Stable identity and serialization rules for the installation-time JSON seed. */
public final class AlgorithmCatalogSeed {
    private static final String ID_NAMESPACE = "cube-solver:algorithm-catalog:";
    private static final ObjectMapper JSON = new ObjectMapper();

    private AlgorithmCatalogSeed() {
    }

    public static List<SeedEntry> entries() {
        var definitions = AlgorithmCaseCatalog.sourceDefinitions();
        return IntStream.range(0, definitions.size())
                .mapToObj(index -> fromSourceDefinition(definitions.get(index), index))
                .toList();
    }

    public static String seedKey(String phase, String name) {
        return "seed-v" + AlgorithmCaseCatalog.VERSION + ":" + phase + ":" + name;
    }

    public static UUID algorithmId(String seedKey) {
        return nameUuid(ID_NAMESPACE + "algorithm:" + seedKey);
    }

    public static UUID revisionId(String seedKey, int revisionNumber) {
        return nameUuid(ID_NAMESPACE + "revision:" + seedKey + ":" + revisionNumber);
    }

    public static String checksum(List<SeedEntry> entries) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var entry : entries) {
                digest.update(entry.definitionJson().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static SeedEntry fromSourceDefinition(SourceDefinition entry, int sourceOrder) {
        var seedKey = seedKey(entry.phase(), entry.name());
        var definition = new LinkedHashMap<String, Object>();
        definition.put("phase", entry.phase());
        definition.put("name", entry.name());
        definition.put("algorithm", entry.algorithm());
        definition.put("notes", entry.notes());
        definition.put("slot", entry.slot());
        definition.put("nonPreservedSlot", entry.nonPreservedSlot());
        definition.put("sourceSetup", entry.sourceSetup());
        definition.put("status", entry.status());
        try {
            return new SeedEntry(
                    seedKey, algorithmId(seedKey), revisionId(seedKey, 1), sourceOrder,
                    entry.phase(), entry.name(), entry.status(), JSON.writeValueAsString(definition)
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize seed definition " + seedKey, exception);
        }
    }

    private static UUID nameUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public record SeedEntry(
            String seedKey,
            UUID algorithmId,
            UUID revisionId,
            int sourceOrder,
            String phase,
            String name,
            String status,
            String definitionJson
    ) {
    }
}
