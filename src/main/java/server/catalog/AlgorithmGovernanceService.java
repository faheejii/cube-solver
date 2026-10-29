package server.catalog;

import algorithms.AlgorithmCaseCatalog;
import algorithms.AlgorithmDatabaseFactory;
import api.AlgorithmDefinitionRequest;
import database.AuthUser;
import database.algorithms.AlgorithmCatalogAlgorithmEntity;
import database.algorithms.AlgorithmCatalogAlgorithmJpaRepository;
import database.algorithms.AlgorithmCatalogRevisionEntity;
import database.algorithms.AlgorithmCatalogRevisionJpaRepository;
import database.algorithms.AlgorithmCatalogStateJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Database-backed algorithm authoring and revision workflow. */
@Service
public class AlgorithmGovernanceService {
    private static final List<String> PHASES = List.of("setup", "insert", "oll", "pll");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AlgorithmCatalogAlgorithmJpaRepository algorithms;
    private final AlgorithmCatalogRevisionJpaRepository revisions;
    private final AlgorithmCatalogStateJpaRepository states;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public AlgorithmGovernanceService(
            AlgorithmCatalogAlgorithmJpaRepository algorithms,
            AlgorithmCatalogRevisionJpaRepository revisions,
            AlgorithmCatalogStateJpaRepository states,
            JdbcTemplate jdbc,
            EntityManager entityManager
    ) {
        this.algorithms = algorithms;
        this.revisions = revisions;
        this.states = states;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(AlgorithmDefinitionRequest definition) {
        var value = normalizeAndValidate(definition);
        return new ValidationResult(true, value.phase(), value.name(), value.algorithm(), value.sourceSetup(),
                value.slot(), value.nonPreservedSlot(),
                isLastLayer(value.phase())
                        ? "OLL/PLL checks use the existing inverse-generated case setup; an independent case setup is not currently modeled."
                        : null);
    }

    @Transactional(readOnly = true)
    public AlgorithmDetail detail(UUID algorithmId, AuthUser actor) {
        requireMaintainer(actor);
        var algorithm = algorithms.findById(algorithmId).orElseThrow(NotFoundException::new);
        var visible = revisions.findAll().stream()
                .filter(revision -> revision.getAlgorithmId().equals(algorithmId))
                .filter(revision -> canSee(revision, algorithm, actor))
                .sorted(Comparator.comparingInt(AlgorithmCatalogRevisionEntity::getRevisionNumber).reversed())
                .map(revision -> revisionView(revision, permittedActions(revision, actor)))
                .toList();
        if (visible.isEmpty()) throw new NotFoundException();
        var visibleIds = visible.stream().map(RevisionView::id).toList();
        var placeholders = String.join(",", java.util.Collections.nCopies(visibleIds.size(), "?"));
        var parameters = new ArrayList<Object>();
        parameters.add(algorithmId);
        parameters.addAll(visibleIds);
        var history = jdbc.query("""
                SELECT action, actor_user_id, revision_id, previous_status, new_status, reason, occurred_at
                FROM algorithm_catalog_workflow_events e
                JOIN algorithm_catalog_revisions r ON r.id = e.revision_id
                WHERE r.algorithm_id = ? AND e.revision_id IN (""" + placeholders + ") " + """
                ORDER BY e.occurred_at, e.id
                """, (rs, row) -> new WorkflowEventView(rs.getString("action"),
                (Long) rs.getObject("actor_user_id"), rs.getObject("revision_id", UUID.class),
                rs.getString("previous_status"), rs.getString("new_status"), rs.getString("reason"),
                rs.getObject("occurred_at", OffsetDateTime.class)), parameters.toArray());
        return new AlgorithmDetail(algorithm.getId(), algorithm.getPhase(), algorithm.getName(),
                algorithm.getOwnerUserId(), algorithm.getActiveCanonicalRevisionId(),
                actor.isAdmin() || algorithm.getOwnerUserId() != null && algorithm.getOwnerUserId() == actor.id(),
                visible, history);
    }

    @Transactional
    public RevisionView create(AlgorithmDefinitionRequest request, AuthUser actor) {
        requireMaintainer(actor);
        lockCatalog();
        var definition = normalizeAndValidate(request);
        if (algorithms.existsByPhaseAndName(definition.phase(), definition.name())) {
            throw new ConflictException("An algorithm with this name already exists in the selected phase");
        }
        rejectDuplicateDefinition(definition);
        var now = now();
        var algorithmId = UUID.randomUUID();
        var revisionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO algorithm_catalog_algorithms
                    (id, phase, name, owner_user_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, algorithmId, definition.phase(), definition.name(), actor.id(), now);
        var revision = new AlgorithmCatalogRevisionEntity(revisionId, algorithmId, 1,
                definition.json(), actor.id(), "experimental", now);
        revisions.saveAndFlush(revision);
        writeEvent("created", actor.id(), revisionId, null, "experimental", null, now);
        return revisionView(revision);
    }

    @Transactional
    public RevisionView createRevision(UUID algorithmId, AlgorithmDefinitionRequest request,
                                       Long expectedVersion, AuthUser actor) {
        requireMaintainer(actor);
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion is required and must be non-negative");
        }
        lockCatalog();
        var algorithm = algorithms.findById(algorithmId).orElseThrow(NotFoundException::new);
        requireCanEdit(algorithm, actor);
        var definition = normalizeAndValidate(request);
        if (!algorithm.getPhase().equals(definition.phase())) {
            throw new IllegalArgumentException("phase cannot change within an algorithm revision lineage; create a separate algorithm");
        }
        if (!algorithm.getName().equals(definition.name())) {
            throw new IllegalArgumentException("name cannot change within an algorithm revision lineage");
        }
        var prior = revisions.findAll().stream().filter(r -> r.getAlgorithmId().equals(algorithmId))
                .max(Comparator.comparingInt(AlgorithmCatalogRevisionEntity::getRevisionNumber))
                .orElseThrow(NotFoundException::new);
        if (prior.getVersion() != expectedVersion) throw new ConflictException("This algorithm changed. Reload its latest revision.");
        var pending = revisions.findAll().stream().filter(r -> r.getAlgorithmId().equals(algorithmId))
                .filter(r -> "experimental".equals(r.getStatus()) || "test-only".equals(r.getStatus()))
                .max(Comparator.comparingInt(AlgorithmCatalogRevisionEntity::getRevisionNumber)).orElse(null);
        if (pending != null && pending.getId().equals(prior.getId())) {
            transition(pending, "deprecated", "superseded", "Superseded by a new experimental revision", actor.id());
        } else if (pending != null) {
            throw new ConflictException("A pending revision already exists. Resolve it before creating another revision.");
        }
        rejectDuplicateDefinition(definition);
        var next = new AlgorithmCatalogRevisionEntity(UUID.randomUUID(), algorithmId,
                prior.getRevisionNumber() + 1, definition.json(), actor.id(), "experimental", now());
        revisions.saveAndFlush(next);
        writeEvent("revision-created", actor.id(), next.getId(), null, "experimental", null, now());
        return revisionView(next);
    }

    @Transactional
    public RevisionView act(UUID algorithmId, UUID revisionId, String action, Long expectedVersion,
                            String reason, AuthUser actor) {
        requireMaintainer(actor);
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion is required and must be non-negative");
        }
        var normalizedAction = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!List.of("submit", "withdraw", "approve", "request-changes", "deprecate", "restore").contains(normalizedAction)) {
            throw new IllegalArgumentException("action must be submit, withdraw, approve, request-changes, deprecate, or restore");
        }
        lockCatalog();
        var algorithm = algorithms.findById(algorithmId).orElseThrow(NotFoundException::new);
        var revision = revisions.findById(revisionId).orElseThrow(NotFoundException::new);
        if (!revision.getAlgorithmId().equals(algorithmId)) throw new NotFoundException();
        if (revision.getVersion() != expectedVersion) throw new ConflictException("This revision changed. Reload it before continuing.");
        requireVisible(revision, algorithm, actor);

        var status = revision.getStatus();
        switch (normalizedAction) {
            case "submit" -> {
                requireAuthorOrAdmin(revision, actor);
                requireStatus(status, "experimental", "Only an experimental revision can be submitted for testing");
                transition(revision, "test-only", normalizedAction, cleanReason(reason), actor.id());
            }
            case "withdraw" -> {
                requireAuthorOrAdmin(revision, actor);
                requireStatus(status, "test-only", "Only a test-only revision can be withdrawn");
                transition(revision, "experimental", normalizedAction, cleanReason(reason), actor.id());
            }
            case "approve" -> {
                requireAdmin(actor);
                requireStatus(status, "test-only", "Only a submitted test-only revision can be approved");
                replaceCanonical(algorithm, revision, actor.id(), cleanReason(reason), "approve");
            }
            case "request-changes" -> {
                requireAdmin(actor);
                requireStatus(status, "test-only", "Changes can only be requested for a test-only revision");
                if (cleanReason(reason) == null) throw new IllegalArgumentException("reason is required when requesting changes");
                transition(revision, "experimental", normalizedAction, reason.trim(), actor.id());
            }
            case "deprecate" -> {
                if ("experimental".equals(status)) {
                    throw new ConflictException("Experimental revisions are controlled with your personal 'Use for my solves' preference, not global deprecation");
                }
                if (!("test-only".equals(status) || "canonical".equals(status))) {
                    throw new ConflictException("Only test-only or canonical revisions can be deprecated");
                }
                requireAdmin(actor);
                transition(revision, "deprecated", normalizedAction, cleanReason(reason), actor.id());
                if (revisionId.equals(algorithm.getActiveCanonicalRevisionId())) {
                    algorithm.setActiveCanonicalRevisionId(null);
                    algorithms.saveAndFlush(algorithm);
                }
            }
            case "restore" -> {
                requireAdmin(actor);
                requireStatus(status, "deprecated", "Only a deprecated revision can be restored");
                if (!wasApproved(revisionId)) {
                    throw new ConflictException("This revision was never approved; submit it for testing before canonical approval");
                }
                replaceCanonical(algorithm, revision, actor.id(), cleanReason(reason), "restore");
            }
            default -> throw new IllegalArgumentException("Unsupported action");
        }
        return revisionView(revisions.findById(revisionId).orElseThrow(NotFoundException::new));
    }

    private void replaceCanonical(AlgorithmCatalogAlgorithmEntity algorithm, AlgorithmCatalogRevisionEntity target,
                                  long actorId, String reason, String action) {
        var activeId = algorithm.getActiveCanonicalRevisionId();
        if (activeId != null && !activeId.equals(target.getId())) {
            var active = revisions.findById(activeId).orElseThrow(() -> new IllegalStateException("Active canonical revision is missing"));
            transition(active, "deprecated", "superseded", "Replaced by revision " + target.getRevisionNumber(), actorId);
        }
        transition(target, "canonical", action, reason, actorId);
        algorithm.setActiveCanonicalRevisionId(target.getId());
        algorithms.saveAndFlush(algorithm);
    }

    private void transition(AlgorithmCatalogRevisionEntity revision, String targetStatus, String action,
                            String reason, long actorId) {
        var previous = revision.getStatus();
        int changed = jdbc.update("""
                UPDATE algorithm_catalog_revisions
                SET status = ?, version = version + 1
                WHERE id = ? AND version = ?
                """, targetStatus, revision.getId(), revision.getVersion());
        if (changed != 1) throw new ConflictException("This revision changed. Reload it before continuing.");
        writeEvent(action, actorId, revision.getId(), previous, targetStatus, reason, now());
        entityManager.refresh(revision);
    }

    private boolean wasApproved(UUID revisionId) {
        return jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM algorithm_catalog_workflow_events
                    WHERE revision_id = ? AND action IN ('approve', 'seed-import-approved'))
                """, Boolean.class, revisionId);
    }

    private void rejectDuplicateDefinition(NormalizedDefinition candidate) {
        for (var algorithm : algorithms.findAll()) {
            if (!candidate.phase().equals(algorithm.getPhase())) continue;
            var hasDuplicate = revisions.findAll().stream()
                    .filter(r -> r.getAlgorithmId().equals(algorithm.getId()))
                    .map(r -> parseDefinition(r.getDefinitionJson()))
                    .anyMatch(existing -> candidate.sameCase(existing) && candidate.algorithm().equals(existing.algorithm()));
            if (hasDuplicate) {
                throw new IllegalArgumentException("This exact algorithm already exists as " + algorithm.getPhase() + ":" + algorithm.getName());
            }
        }
    }

    private static NormalizedDefinition normalizeAndValidate(AlgorithmDefinitionRequest request) {
        if (request == null) throw new IllegalArgumentException("definition is required");
        var phase = normalizeRequired(request.phase(), "phase").toLowerCase(Locale.ROOT);
        if (!PHASES.contains(phase)) throw new IllegalArgumentException("phase must be setup, insert, oll, or pll");
        var name = normalizeRequired(request.name(), "name");
        var algorithm = normalizeRequired(request.algorithm(), "algorithm");
        var notes = request.notes() == null ? null : request.notes().trim();
        var slot = nullableTrim(request.slot());
        var nonPreserved = nullableTrim(request.nonPreservedSlot());
        var sourceSetup = nullableTrim(request.sourceSetup());
        try {
            if ("setup".equals(phase)) {
                requireValue(nonPreserved, "nonPreservedSlot is required for an F2L setup definition");
                requireValue(sourceSetup, "sourceSetup is required for an F2L setup definition");
            } else if ("insert".equals(phase)) {
                requireValue(slot, "slot is required for an F2L insert definition");
            } else if (slot != null || nonPreserved != null || sourceSetup != null) {
                throw new IllegalArgumentException("slot/source setup fields are only valid for F2L definitions");
            }
            var parsedSlot = slot == null ? null : cfop.F2LSlot.valueOf(slot.toUpperCase(Locale.ROOT));
            var parsedNonPreserved = nonPreserved == null ? null : cfop.F2LSlot.valueOf(nonPreserved.toUpperCase(Locale.ROOT));
            var normalizedAlgorithm = cube.Algorithm.parse(util.NotationNormalizer.normalizePrimes(algorithm)).toString();
            var normalizedSetup = sourceSetup == null ? null
                    : cube.Algorithm.parse(util.NotationNormalizer.normalizePrimes(sourceSetup)).toString();
            var entry = new AlgorithmCaseCatalog.CatalogEntry(phase, name, parsedSlot, parsedNonPreserved,
                    null, null, normalizedAlgorithm, normalizedSetup, null, "experimental", notes);
            var indexes = AlgorithmDatabaseFactory.build(List.of(entry));
            var registeredCount = switch (phase) {
                case "setup" -> indexes.setup().size();
                case "insert" -> indexes.insert().size();
                case "oll" -> indexes.oll().size();
                case "pll" -> indexes.pll().size();
                default -> 0;
            };
            if (registeredCount != 1) {
                throw new IllegalArgumentException("the case did not produce a valid solver index entry");
            }
            if (isLastLayer(phase)) {
                validateAgainstKnownLastLayerCases(phase, cube.Algorithm.parse(normalizedAlgorithm));
            }
            return new NormalizedDefinition(phase, name, normalizedAlgorithm, notes,
                    parsedSlot == null ? null : parsedSlot.name(),
                    parsedNonPreserved == null ? null : parsedNonPreserved.name(), normalizedSetup);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + phase + " definition: " + correction(exception.getMessage()), exception);
        }
    }

    private static void validateAgainstKnownLastLayerCases(String phase, cube.Algorithm candidate) {
        if ("oll".equals(phase)) {
            for (var knownCase : AlgorithmCaseCatalog.ollDatabase().allCases()) {
                var setup = new cube.OrientedCube();
                setup.applyMoves(knownCase.algorithm().inverse().getMoves());
                if (!cfop.CrossAnalyzer.isCrossSolved(setup.cubeState(), setup.orientation())
                        || !cfop.F2LAnalyzer.isF2LSolved(setup.cubeState(), setup.orientation())) continue;
                var result = new cube.OrientedCube(setup.cubeState().copy(), setup.orientation());
                result.applyMoves(candidate.getMoves());
                if (cfop.CrossAnalyzer.isCrossSolved(result.cubeState(), result.orientation())
                        && cfop.F2LAnalyzer.isF2LSolved(result.cubeState(), result.orientation())
                        && cfop.OLLAnalyzer.isOllSolved(result.cubeState(), result.orientation())) return;
            }
            throw new IllegalArgumentException("OLL algorithm does not solve any known OLL case while preserving cross and F2L");
        }
        for (var knownCase : AlgorithmCaseCatalog.pllDatabase().allCases()) {
            var setup = new cube.OrientedCube();
            setup.applyMoves(knownCase.algorithm().inverse().getMoves());
            if (!cfop.CrossAnalyzer.isCrossSolved(setup.cubeState(), setup.orientation())
                    || !cfop.F2LAnalyzer.isF2LSolved(setup.cubeState(), setup.orientation())
                    || !cfop.OLLAnalyzer.isOllSolved(setup.cubeState(), setup.orientation())) continue;
            var result = new cube.OrientedCube(setup.cubeState().copy(), setup.orientation());
            result.applyMoves(candidate.getMoves());
            if (cfop.CrossAnalyzer.isCrossSolved(result.cubeState(), result.orientation())
                    && cfop.F2LAnalyzer.isF2LSolved(result.cubeState(), result.orientation())
                    && cfop.OLLAnalyzer.isOllSolved(result.cubeState(), result.orientation())
                    && cfop.PLLAnalyzer.isPllSolved(result.cubeState(), result.orientation())) return;
        }
        throw new IllegalArgumentException("PLL algorithm does not solve any known PLL case while preserving cross, F2L, and OLL");
    }

    private static String correction(String diagnostic) {
        if (diagnostic == null || diagnostic.isBlank()) return "check the notation and required phase fields";
        var lower = diagnostic.toLowerCase(Locale.ROOT);
        if (lower.contains("preserve") || lower.contains("solve") || lower.contains("cross")) {
            return diagnostic + ". Check the supplied algorithm and phase-specific setup fields.";
        }
        return diagnostic + ". Check the move notation and use the required phase-specific fields.";
    }

    private static NormalizedDefinition parseDefinition(String json) {
        try {
            var node = JSON.readTree(json);
            return new NormalizedDefinition(node.path("phase").asText(), node.path("name").asText(),
                    cube.Algorithm.parse(util.NotationNormalizer.normalizePrimes(node.path("algorithm").asText())).toString(),
                    node.path("notes").isMissingNode() || node.path("notes").isNull() ? null : node.path("notes").asText(),
                    nullableNode(node, "slot"), nullableNode(node, "nonPreservedSlot"),
                    node.path("sourceSetup").isMissingNode() || node.path("sourceSetup").isNull() ? null
                            : cube.Algorithm.parse(util.NotationNormalizer.normalizePrimes(node.path("sourceSetup").asText())).toString());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored algorithm definition is malformed", exception);
        }
    }

    private static String nullableNode(tools.jackson.databind.JsonNode node, String key) {
        return node.path(key).isMissingNode() || node.path(key).isNull() ? null : node.path(key).asText();
    }

    private static RevisionView revisionView(AlgorithmCatalogRevisionEntity revision) {
        return revisionView(revision, List.of());
    }

    private static RevisionView revisionView(AlgorithmCatalogRevisionEntity revision, List<String> permittedActions) {
        return new RevisionView(revision.getId(), revision.getAlgorithmId(), revision.getRevisionNumber(),
                revision.getDefinitionJson(), revision.getAuthorUserId(), revision.getStatus(), revision.getCreatedAt(),
                revision.getVersion(), permittedActions);
    }

    private List<String> permittedActions(AlgorithmCatalogRevisionEntity revision, AuthUser actor) {
        var isAuthor = revision.getAuthorUserId() != null && revision.getAuthorUserId() == actor.id();
        var isAdmin = actor.isAdmin();
        return switch (revision.getStatus()) {
            case "experimental" -> isAdmin || isAuthor ? List.of("submit") : List.of();
            case "test-only" -> isAdmin ? List.of("withdraw", "approve", "request-changes", "deprecate")
                    : isAuthor ? List.of("withdraw") : List.of();
            case "canonical" -> isAdmin ? List.of("deprecate") : List.of();
            case "deprecated" -> isAdmin && wasApproved(revision.getId()) ? List.of("restore") : List.of();
            default -> List.of();
        };
    }

    private boolean canSee(AlgorithmCatalogRevisionEntity revision, AlgorithmCatalogAlgorithmEntity algorithm, AuthUser actor) {
        if (actor.isAdmin() || algorithm.getSeedKey() != null) return true;
        return switch (revision.getStatus()) {
            case "experimental" -> (revision.getAuthorUserId() != null && revision.getAuthorUserId() == actor.id())
                    || (algorithm.getOwnerUserId() != null && algorithm.getOwnerUserId() == actor.id());
            case "test-only", "canonical" -> true;
            case "deprecated" -> wasEverShared(revision.getId())
                    || (revision.getAuthorUserId() != null && revision.getAuthorUserId() == actor.id())
                    || (algorithm.getOwnerUserId() != null && algorithm.getOwnerUserId() == actor.id());
            default -> false;
        };
    }

    private boolean wasEverShared(UUID revisionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM algorithm_catalog_workflow_events
                    WHERE revision_id = ? AND new_status IN ('test-only', 'canonical'))
                """, Boolean.class, revisionId));
    }

    private void requireVisible(AlgorithmCatalogRevisionEntity revision, AlgorithmCatalogAlgorithmEntity algorithm, AuthUser actor) {
        if (!canSee(revision, algorithm, actor)) throw new NotFoundException();
    }

    private static void requireCanEdit(AlgorithmCatalogAlgorithmEntity algorithm, AuthUser actor) {
        if (!actor.isAdmin() && (algorithm.getOwnerUserId() == null || algorithm.getOwnerUserId() != actor.id())) {
            throw new NotFoundException();
        }
    }

    private static void requireAuthorOrAdmin(AlgorithmCatalogRevisionEntity revision, AuthUser actor) {
        if (!actor.isAdmin() && (revision.getAuthorUserId() == null || revision.getAuthorUserId() != actor.id())) {
            throw new NotFoundException();
        }
    }

    private static void requireMaintainer(AuthUser actor) {
        if (actor == null || !actor.isMaintainer()) throw new server.auth.AuthOperations.ForbiddenException("Maintainer access required");
    }

    private static void requireAdmin(AuthUser actor) {
        if (actor == null || !actor.isAdmin()) throw new server.auth.AuthOperations.ForbiddenException("Administrator access required");
    }

    private static void requireStatus(String actual, String expected, String message) {
        if (!expected.equals(actual)) throw new ConflictException(message);
    }

    private void lockCatalog() {
        states.findSingletonForUpdate().orElseThrow(() -> new IllegalStateException("Algorithm catalog state row is missing"));
    }

    private void writeEvent(String action, long actorId, UUID revisionId, String before, String after, String reason, OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO algorithm_catalog_workflow_events
                    (action, actor_user_id, revision_id, previous_status, new_status, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, action, actorId, revisionId, before, after, reason, at);
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String nullableTrim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void requireValue(String value, String message) { if (value == null) throw new IllegalArgumentException(message); }
    private static String cleanReason(String reason) { return reason == null || reason.isBlank() ? null : reason.trim(); }
    private static boolean isLastLayer(String phase) { return "oll".equals(phase) || "pll".equals(phase); }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }

    private record NormalizedDefinition(String phase, String name, String algorithm, String notes,
                                        String slot, String nonPreservedSlot, String sourceSetup) {
        boolean sameCase(NormalizedDefinition other) {
            return phase.equals(other.phase) && java.util.Objects.equals(slot, other.slot)
                    && java.util.Objects.equals(nonPreservedSlot, other.nonPreservedSlot)
                    && java.util.Objects.equals(sourceSetup, other.sourceSetup);
        }
        String json() {
            try {
                var definition = new java.util.LinkedHashMap<String, Object>();
                definition.put("phase", phase);
                definition.put("name", name);
                definition.put("algorithm", algorithm);
                definition.put("notes", notes);
                definition.put("slot", slot);
                definition.put("nonPreservedSlot", nonPreservedSlot);
                definition.put("sourceSetup", sourceSetup);
                return JSON.writeValueAsString(definition);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not serialize algorithm definition", exception);
            }
        }
    }

    public record ValidationResult(boolean valid, String phase, String name, String algorithm, String sourceSetup,
                                   String slot, String nonPreservedSlot, String validatorNote) { }
    public record RevisionView(UUID id, UUID algorithmId, int revisionNumber, String definitionJson,
                               Long authorUserId, String status, OffsetDateTime createdAt, long version,
                               List<String> permittedActions) { }
    public record WorkflowEventView(String action, Long actorUserId, UUID revisionId, String previousStatus,
                                    String newStatus, String reason, OffsetDateTime occurredAt) { }
    public record AlgorithmDetail(UUID id, String phase, String name, Long ownerUserId,
                                  UUID activeCanonicalRevisionId, boolean canCreateRevision, List<RevisionView> revisions,
                                  List<WorkflowEventView> history) { }

    public static class NotFoundException extends RuntimeException { }
    public static class ConflictException extends server.auth.AuthOperations.ConflictException {
        public ConflictException(String message) { super(message); }
    }
}
