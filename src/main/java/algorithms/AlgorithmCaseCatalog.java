package algorithms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cfop.F2LCaseSignature;
import cfop.F2LSlot;
import cfop.OLLCaseSignature;
import cfop.PLLCaseSignature;
import cube.Algorithm;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads and validates the versioned canonical algorithm corpora shared by solver and catalog API. */
public final class AlgorithmCaseCatalog {
    public static final String VERSION = "1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SETUP_RESOURCE = "/algorithms/f2l/setup-cases.json";
    private static final String INSERT_RESOURCE = "/algorithms/f2l/insert-cases.json";
    private static final String OLL_RESOURCE = "/algorithms/oll/cases.json";
    private static final String PLL_RESOURCE = "/algorithms/pll/cases.json";

    private AlgorithmCaseCatalog() {
    }

    public static F2LSetupCaseDatabase setupDatabase() {
        var corpus = readCorpus();
        var database = F2LSetupCaseDatabase.empty();
        for (var definition : corpus.setupDefinitions()) {
            if (!"canonical".equals(definition.status())) {
                continue;
            }
            database.register(
                    definition.sourceSetup(),
                    definition.algorithm(),
                    definition.nonPreservedSlot(),
                    definition.name()
            );
        }
        database.validate();
        return database;
    }

    public static F2LInsertCaseDatabase insertDatabase() {
        var corpus = readCorpus();
        var database = F2LInsertCaseDatabase.empty();
        for (var definition : corpus.insertDefinitions()) {
            if (!"canonical".equals(definition.status())) {
                continue;
            }
            database.register(definition.algorithm(), definition.slot(), definition.name());
        }
        database.validate();
        return database;
    }

    public static OLLCaseDatabase ollDatabase() {
        var database = OLLCaseDatabase.empty();
        for (var definition : readCases(OLL_RESOURCE)) {
            if ("canonical".equals(definition.status())) {
                database.register(definition.algorithm(), definition.name());
            }
        }
        database.validate();
        return database;
    }

    public static PLLCaseDatabase pllDatabase() {
        var database = PLLCaseDatabase.empty();
        for (var definition : readCases(PLL_RESOURCE)) {
            if ("canonical".equals(definition.status())) {
                database.register(definition.algorithm(), definition.name());
            }
        }
        database.validate();
        return database;
    }

    public static List<CatalogEntry> entries(boolean includeNonCanonical) {
        var corpus = readCorpus();
        var setupDatabase = F2LSetupCaseDatabase.empty();
        var sourceByKey = new HashMap<String, Definition>();
        for (var definition : corpus.setupDefinitions()) {
            sourceByKey.put(definition.name() + "|" + definition.sourceSetup(), definition);
            if (includeNonCanonical || "canonical".equals(definition.status())) {
                setupDatabase.register(
                        definition.sourceSetup(), definition.algorithm(),
                        definition.nonPreservedSlot(), definition.name()
                );
            }
        }
        setupDatabase.validate();

        var entries = new ArrayList<CatalogEntry>();
        for (var setupCase : setupDatabase.allCases()) {
            var definition = sourceByKey.get(setupCase.name() + "|" + setupCase.sourceSetup());
            if (definition == null) {
                throw new IllegalStateException("Missing catalog definition for setup case " + setupCase.name());
            }
            entries.add(new CatalogEntry(
                    "setup", setupCase.name(), null, setupCase.nonPreservedSlot(), setupCase.preservedSlots(),
                    setupCase.signature(), setupCase.algorithm().toString(),
                    setupCase.sourceSetup().toString(), setupCase.sourceSetup().toString(),
                    definition.status(), definition.notes()
            ));
        }

        for (var definition : corpus.insertDefinitions()) {
            if (!includeNonCanonical && !"canonical".equals(definition.status())) {
                continue;
            }
            var insertDatabase = F2LInsertCaseDatabase.empty();
            insertDatabase.register(definition.algorithm(), definition.slot(), definition.name());
            insertDatabase.validate();
            for (var insertCase : insertDatabase.allCases()) {
                entries.add(new CatalogEntry(
                        "insert", insertCase.name(), insertCase.insertSlot(), null, insertCase.preservedSlots(),
                        insertCase.signature(), insertCase.algorithm().toString(),
                        null, null, definition.status(), definition.notes()
                ));
            }
        }
        for (var definition : readCases(OLL_RESOURCE)) {
            if (!includeNonCanonical && !"canonical".equals(definition.status())) {
                continue;
            }
            var database = OLLCaseDatabase.empty();
            database.register(definition.algorithm(), definition.name());
            database.validate();
            var ollCase = database.allCases().iterator().next();
            entries.add(new CatalogEntry(
                    "oll", ollCase.name(), null, null, null, ollCase.signature(),
                    ollCase.algorithm().toString(), null, ollCase.algorithm().inverse().toString(),
                    definition.status(), definition.notes()
            ));
        }
        for (var definition : readCases(PLL_RESOURCE)) {
            if (!includeNonCanonical && !"canonical".equals(definition.status())) {
                continue;
            }
            var database = PLLCaseDatabase.empty();
            database.register(definition.algorithm(), definition.name());
            var pllCase = database.allCases().iterator().next();
            entries.add(new CatalogEntry(
                    "pll", pllCase.name(), null, null, null, pllCase.signature(),
                    pllCase.algorithm().toString(), null, pllCase.algorithm().inverse().toString(),
                    definition.status(), definition.notes()
            ));
        }
        entries.sort(Comparator.comparing(CatalogEntry::phase).thenComparing(CatalogEntry::name));
        return List.copyOf(entries);
    }

    private static Corpus readCorpus() {
        var setupRoot = readResource(SETUP_RESOURCE);
        var insertRoot = readResource(INSERT_RESOURCE);
        requireVersion(setupRoot, SETUP_RESOURCE);
        requireVersion(insertRoot, INSERT_RESOURCE);
        return new Corpus(
                readDefinitions(setupRoot, true, SETUP_RESOURCE),
                readDefinitions(insertRoot, false, INSERT_RESOURCE)
        );
    }

    private static List<Definition> readCases(String resource) {
        var root = readResource(resource);
        requireVersion(root, resource);
        return readDefinitions(root, resource);
    }

    private static List<Definition> readDefinitions(JsonNode root, boolean setup, String resource) {
        var cases = root.get("cases");
        if (cases == null || !cases.isArray() || cases.isEmpty()) {
            throw new IllegalStateException(resource + " must contain a non-empty cases array");
        }
        var definitions = new ArrayList<Definition>();
        Set<String> names = new HashSet<>();
        for (var node : cases) {
            var name = requiredText(node, "name", resource);
            if (!names.add(name)) {
                throw new IllegalStateException(resource + " contains duplicate case name " + name);
            }
            var algorithm = requiredText(node, "algorithm", resource + " case " + name);
            var status = requiredStatus(node, resource + " case " + name);
            var notes = optionalText(node, "notes");
            if (setup) {
                definitions.add(new Definition(
                        name,
                        F2LSlot.valueOf(requiredText(node, "nonPreservedSlot", resource + " case " + name)),
                        null,
                        algorithm,
                        requiredText(node, "sourceSetup", resource + " case " + name),
                        status,
                        notes
                ));
            } else {
                definitions.add(new Definition(
                        name,
                        null,
                        F2LSlot.valueOf(requiredText(node, "slot", resource + " case " + name)),
                        algorithm,
                        null,
                        status,
                        notes
                ));
            }
        }
        return List.copyOf(definitions);
    }

    private static List<Definition> readDefinitions(JsonNode root, String resource) {
        var cases = root.get("cases");
        if (cases == null || !cases.isArray() || cases.isEmpty()) {
            throw new IllegalStateException(resource + " must contain a non-empty cases array");
        }
        var definitions = new ArrayList<Definition>();
        Set<String> names = new HashSet<>();
        for (var node : cases) {
            var name = requiredText(node, "name", resource);
            if (!names.add(name)) {
                throw new IllegalStateException(resource + " contains duplicate case name " + name);
            }
            definitions.add(new Definition(
                    name,
                    null,
                    null,
                    requiredText(node, "algorithm", resource + " case " + name),
                    null,
                    requiredStatus(node, resource + " case " + name),
                    optionalText(node, "notes")
            ));
        }
        return List.copyOf(definitions);
    }

    private static JsonNode readResource(String resource) {
        try (InputStream stream = AlgorithmCaseCatalog.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing algorithm corpus resource " + resource);
            }
            return JSON.readTree(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read algorithm corpus resource " + resource, exception);
        }
    }

    private static void requireVersion(JsonNode root, String resource) {
        if (root == null || !VERSION.equals(optionalText(root, "version"))) {
            throw new IllegalStateException(resource + " has unsupported corpus version");
        }
    }

    private static String requiredText(JsonNode node, String field, String context) {
        var value = optionalText(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(context + " requires text field " + field);
        }
        return value;
    }

    private static String requiredStatus(JsonNode node, String context) {
        var status = requiredText(node, "status", context);
        if (!Set.of("canonical", "experimental", "deprecated", "test-only").contains(status)) {
            throw new IllegalStateException(context + " has unsupported status " + status);
        }
        return status;
    }

    private static String optionalText(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static String baseName(String expandedName) {
        var separator = expandedName.lastIndexOf('-');
        return separator < 0 ? expandedName : expandedName.substring(0, separator);
    }

    private record Corpus(List<Definition> setupDefinitions, List<Definition> insertDefinitions) {
    }

    private record Definition(
            String name,
            F2LSlot nonPreservedSlot,
            F2LSlot slot,
            String algorithm,
            String sourceSetup,
            String status,
            String notes
    ) {
    }

    public record CatalogEntry(
            String phase,
            String name,
            F2LSlot slot,
            F2LSlot nonPreservedSlot,
            cfop.F2LPreservationMask preservedSlots,
            Object signature,
            String algorithm,
            String sourceSetup,
            String previewSetup,
            String status,
            String notes
    ) {
    }
}
