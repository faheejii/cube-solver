package server.catalog;

import algorithms.AlgorithmCaseCatalog;
import algorithms.AlgorithmDatabaseFactory;
import api.AlgorithmDefinitionRequest;
import database.AuthUser;
import database.algorithms.AlgorithmCatalogAlgorithmJpaRepository;
import database.algorithms.AlgorithmCatalogRevisionEntity;
import database.algorithms.AlgorithmCatalogRevisionJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Read model for the permission-filtered algorithm workspace. */
@Service
public class AlgorithmWorkspaceCatalogService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AlgorithmCatalogAlgorithmJpaRepository algorithms;
    private final AlgorithmCatalogRevisionJpaRepository revisions;
    private final JdbcTemplate jdbc;
    private final java.util.concurrent.ConcurrentHashMap<UUID, CachedProjection> projections = new java.util.concurrent.ConcurrentHashMap<>();

    public AlgorithmWorkspaceCatalogService(
            AlgorithmCatalogAlgorithmJpaRepository algorithms,
            AlgorithmCatalogRevisionJpaRepository revisions,
            JdbcTemplate jdbc
    ) {
        this.algorithms = algorithms;
        this.revisions = revisions;
        this.jdbc = jdbc;
    }

    public CatalogPage list(AuthUser actor, String phase, String slot, String status, String scope, String query,
                            boolean includeNonCanonical, String cursor, int requestedLimit) {
        if (actor == null || !actor.isMaintainer()) {
            throw new server.auth.AuthOperations.ForbiddenException("Maintainer access required");
        }
        var normalizedPhase = clean(phase).toLowerCase(Locale.ROOT);
        var normalizedSlot = clean(slot).toUpperCase(Locale.ROOT);
        var normalizedStatus = clean(status).toLowerCase(Locale.ROOT);
        var normalizedScope = clean(scope).toLowerCase(Locale.ROOT);
        var normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
        var limit = requestedLimit <= 0 ? DEFAULT_LIMIT : Math.min(requestedLimit, MAX_LIMIT);

        var revisionById = new HashMap<UUID, AlgorithmCatalogRevisionEntity>();
        revisions.findAll().forEach(revision -> revisionById.put(revision.getId(), revision));
        var visible = new ArrayList<WorkspaceItem>();
        for (var algorithm : algorithms.findAll()) {
            var candidates = revisionById.values().stream()
                    .filter(revision -> revision.getAlgorithmId().equals(algorithm.getId()))
                    .sorted(Comparator.comparingInt(AlgorithmCatalogRevisionEntity::getRevisionNumber)).toList();
            for (var revision : candidates) {
                if (!isVisible(actor, algorithm.getOwnerUserId(), algorithm.getSeedKey(), revision)) continue;
                if ("my-experimental".equals(normalizedScope)
                        && (!"experimental".equals(revision.getStatus())
                        || revision.getAuthorUserId() == null || revision.getAuthorUserId() != actor.id())) continue;
                if ("shared-testing".equals(normalizedScope) && !"test-only".equals(revision.getStatus())) continue;
                if ("review-queue".equals(normalizedScope)
                        && (!actor.isAdmin() || !"test-only".equals(revision.getStatus()))) continue;
                if (!includeNonCanonical && !revision.getId().equals(algorithm.getActiveCanonicalRevisionId())) continue;
                if (!normalizedStatus.isBlank() && !"all".equals(normalizedStatus)
                        && !normalizedStatus.equals(revision.getStatus())) continue;
                var item = project(actor, algorithm.getId(), algorithm.getOwnerUserId(), algorithm.getSeedOrder(), revision);
                if (!normalizedPhase.isBlank() && !normalizedPhase.equals(item.definition().phase())) continue;
                if (!normalizedSlot.isBlank() && !matchesSlot(item.definition(), normalizedSlot)) continue;
                if (!normalizedQuery.isBlank()
                        && !item.definition().name().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                        && !item.definition().algorithm().toLowerCase(Locale.ROOT).contains(normalizedQuery)) continue;
                visible.add(item);
            }
        }
        visible.sort(Comparator.comparing((WorkspaceItem item) -> item.definition().phase())
                .thenComparing(WorkspaceItem::sourceOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> item.definition().name())
                .thenComparingInt(WorkspaceItem::revisionNumber)
                .thenComparing(item -> item.revisionId().toString()));

        if (cursor != null && !cursor.isBlank()) {
            var cursorKey = decodeCursor(cursor);
            var cursorIndex = -1;
            for (int index = 0; index < visible.size(); index++) {
                var candidate = visible.get(index);
                if (candidate.algorithmId().equals(cursorKey.algorithmId())
                        && candidate.revisionId().equals(cursorKey.revisionId())) {
                    cursorIndex = index;
                    break;
                }
            }
            if (cursorIndex < 0) throw new IllegalArgumentException("Invalid algorithm catalog cursor");
            visible = new ArrayList<>(visible.subList(cursorIndex + 1, visible.size()));
        }

        var hasMore = visible.size() > limit;
        var pageItems = List.copyOf(visible.subList(0, Math.min(limit, visible.size())));
        var nextCursor = hasMore && !pageItems.isEmpty()
                ? encodeCursor(pageItems.getLast().algorithmId(), pageItems.getLast().revisionId()) : null;
        return new CatalogPage(pageItems, nextCursor);
    }

    private boolean isVisible(AuthUser actor, Long ownerId, String seedKey, AlgorithmCatalogRevisionEntity revision) {
        if (actor.isAdmin() || seedKey != null) return true;
        return switch (revision.getStatus()) {
            case "canonical", "test-only" -> true;
            case "experimental" -> revision.getAuthorUserId() != null && revision.getAuthorUserId() == actor.id()
                    || ownerId != null && ownerId == actor.id();
            case "deprecated" -> wasEverShared(revision.getId())
                    || revision.getAuthorUserId() != null && revision.getAuthorUserId() == actor.id()
                    || ownerId != null && ownerId == actor.id();
            default -> false;
        };
    }

    private boolean wasEverShared(UUID revisionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM algorithm_catalog_workflow_events
                    WHERE revision_id = ? AND new_status IN ('test-only', 'canonical'))
                """, Boolean.class, revisionId));
    }

    private WorkspaceItem project(AuthUser actor, UUID algorithmId, Long ownerUserId, Integer sourceOrder,
                                  AlgorithmCatalogRevisionEntity revision) {
        var cached = projections.get(revision.getId());
        var projected = cached != null && cached.version() == revision.getVersion()
                && cached.status().equals(revision.getStatus()) ? cached.entry() : derive(revision);
        projections.put(revision.getId(), new CachedProjection(revision.getVersion(), revision.getStatus(), projected));
        if (projections.size() > 1024) {
            projections.clear();
            projections.put(revision.getId(), new CachedProjection(revision.getVersion(), revision.getStatus(), projected));
        }
        return new WorkspaceItem(algorithmId, revision.getId(), revision.getRevisionNumber(), revision.getVersion(),
                ownerUserId, sourceOrder, projected, trialEligible(actor, revision), permittedActions(revision, actor));
    }

    private static AlgorithmCaseCatalog.CatalogEntry derive(AlgorithmCatalogRevisionEntity revision) {
        try {
            var definition = parseDefinition(revision.getDefinitionJson(), revision.getStatus());
            var indexes = AlgorithmDatabaseFactory.build(List.of(definition));
            var projected = switch (definition.phase()) {
                case "setup" -> indexes.setup().allCases().stream().findFirst().map(value -> new AlgorithmCaseCatalog.CatalogEntry(
                        "setup", definition.name(), null, value.nonPreservedSlot(), value.preservedSlots(), value.signature(),
                        value.algorithm().toString(), value.sourceSetup().toString(), value.sourceSetup().toString(),
                        revision.getStatus(), definition.notes())).orElse(null);
                case "insert" -> indexes.insert().allCases().stream().findFirst().map(value -> new AlgorithmCaseCatalog.CatalogEntry(
                        "insert", definition.name(), value.insertSlot(), null, value.preservedSlots(), value.signature(),
                        value.algorithm().toString(), null, null, revision.getStatus(), definition.notes())).orElse(null);
                case "oll" -> indexes.oll().allCases().stream().findFirst().map(value -> new AlgorithmCaseCatalog.CatalogEntry(
                        "oll", definition.name(), null, null, null, value.signature(), value.algorithm().toString(), null,
                        value.algorithm().inverse().toString(), revision.getStatus(), definition.notes())).orElse(null);
                case "pll" -> indexes.pll().allCases().stream().findFirst().map(value -> new AlgorithmCaseCatalog.CatalogEntry(
                        "pll", definition.name(), null, null, null, value.signature(), value.algorithm().toString(), null,
                        value.algorithm().inverse().toString(), revision.getStatus(), definition.notes())).orElse(null);
                default -> null;
            };
            if (projected == null) {
                // Some installation seed definitions are preserved in PostgreSQL for fidelity even
                // though the legacy solver index intentionally excludes them from runtime lookup.
                projected = new AlgorithmCaseCatalog.CatalogEntry(definition.phase(), definition.name(),
                        definition.slot(), definition.nonPreservedSlot(), null, null,
                        definition.algorithm(), definition.sourceSetup(), definition.sourceSetup(),
                        revision.getStatus(), definition.notes());
            }
            return projected;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not derive algorithm catalog entry " + revision.getId(), exception);
        }
    }

    private record CachedProjection(long version, String status, AlgorithmCaseCatalog.CatalogEntry entry) { }

    private static AlgorithmCaseCatalog.CatalogEntry parseDefinition(String json, String status) {
        try {
            JsonNode node = JSON.readTree(json);
            var phase = node.path("phase").asText();
            var name = node.path("name").asText();
            var algorithm = node.path("algorithm").asText();
            var notesNode = node.get("notes");
            var notes = notesNode == null || notesNode.isNull() ? "" : notesNode.asText();
            var slot = nullableText(node, "slot");
            var nonPreservedSlot = nullableText(node, "nonPreservedSlot");
            var sourceSetup = nullableText(node, "sourceSetup");
            return new AlgorithmCaseCatalog.CatalogEntry(phase, name,
                    slot == null ? null : cfop.F2LSlot.valueOf(slot),
                    nonPreservedSlot == null ? null : cfop.F2LSlot.valueOf(nonPreservedSlot),
                    null, null, algorithm, sourceSetup, null, status, notes);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored algorithm definition is malformed", exception);
        }
    }

    private static String nullableText(JsonNode node, String key) {
        var value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<String> permittedActions(AlgorithmCatalogRevisionEntity revision, AuthUser actor) {
        var isAuthor = actor != null && revision.getAuthorUserId() != null
                && revision.getAuthorUserId() == actor.id();
        var isAdmin = actor != null && actor.isAdmin();
        return switch (revision.getStatus()) {
            case "experimental" -> isAdmin || isAuthor ? List.of("submit") : List.of();
            case "test-only" -> isAdmin ? List.of("withdraw", "approve", "request-changes", "deprecate")
                    : isAuthor ? List.of("withdraw") : List.of();
            case "canonical" -> isAdmin ? List.of("deprecate") : List.of();
            case "deprecated" -> isAdmin ? List.of("restore") : List.of();
            default -> List.of();
        };
    }

    private static boolean trialEligible(AuthUser actor, AlgorithmCatalogRevisionEntity revision) {
        if (actor == null || !actor.isMaintainer()) return false;
        return "test-only".equals(revision.getStatus())
                || "experimental".equals(revision.getStatus()) && revision.getAuthorUserId() != null
                && revision.getAuthorUserId() == actor.id();
    }

    private static boolean matchesSlot(AlgorithmCaseCatalog.CatalogEntry entry, String slot) {
        return entry.slot() != null && entry.slot().name().equals(slot)
                || entry.nonPreservedSlot() != null && entry.nonPreservedSlot().name().equals(slot);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    private static String encodeCursor(UUID algorithmId, UUID revisionId) {
        var value = algorithmId + ":" + revisionId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorKey decodeCursor(String cursor) {
        try {
            var value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":", 2);
            return new CursorKey(UUID.fromString(value[0]), UUID.fromString(value[1]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid algorithm catalog cursor");
        }
    }

    private record CursorKey(UUID algorithmId, UUID revisionId) { }

    public record WorkspaceItem(UUID algorithmId, UUID revisionId, int revisionNumber, long revisionVersion,
                                Long ownerUserId, Integer sourceOrder, AlgorithmCaseCatalog.CatalogEntry definition,
                                boolean trialEligible,
                                List<String> permittedActions) {
        public WorkspaceItem { permittedActions = List.copyOf(permittedActions); }
    }

    public record CatalogPage(List<WorkspaceItem> items, String nextCursor) {
        public CatalogPage { items = List.copyOf(items); }
    }
}
