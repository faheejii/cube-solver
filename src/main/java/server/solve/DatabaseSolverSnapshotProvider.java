package server.solve;

import algorithms.AlgorithmCaseCatalog;
import algorithms.AlgorithmDatabaseFactory;
import cfop.F2LSlot;
import database.AuthUser;
import database.algorithms.AlgorithmCatalogAlgorithmEntity;
import database.algorithms.AlgorithmCatalogAlgorithmJpaRepository;
import database.algorithms.AlgorithmCatalogRevisionEntity;
import database.algorithms.AlgorithmCatalogRevisionJpaRepository;
import database.algorithms.AlgorithmCatalogStateJpaRepository;
import database.algorithms.UserDisabledAlgorithmJpaRepository;
import database.algorithms.UserEnabledTrialRevisionJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import solver.CfopSolveService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Resolves the database catalog and personal preferences into one immutable solve-time snapshot. */
@Service
public class DatabaseSolverSnapshotProvider {
    private static final int MAX_CONTEXTS = 16;

    private final AlgorithmCatalogAlgorithmJpaRepository algorithms;
    private final AlgorithmCatalogRevisionJpaRepository revisions;
    private final AlgorithmCatalogStateJpaRepository catalogState;
    private final UserDisabledAlgorithmJpaRepository disabledPreferences;
    private final UserEnabledTrialRevisionJpaRepository trialPreferences;
    private final ObjectMapper mapper;
    private final LinkedHashMap<List<UUID>, CfopSolveService> solverContexts = new LinkedHashMap<>(16, 0.75f, true);

    public DatabaseSolverSnapshotProvider(
            AlgorithmCatalogAlgorithmJpaRepository algorithms,
            AlgorithmCatalogRevisionJpaRepository revisions,
            AlgorithmCatalogStateJpaRepository catalogState,
            UserDisabledAlgorithmJpaRepository disabledPreferences,
            UserEnabledTrialRevisionJpaRepository trialPreferences,
            ObjectMapper mapper
    ) {
        this.algorithms = algorithms;
        this.revisions = revisions;
        this.catalogState = catalogState;
        this.disabledPreferences = disabledPreferences;
        this.trialPreferences = trialPreferences;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SolverSnapshot capture(AuthUser user) {
        var version = catalogState.findById((short) 1)
                .orElseThrow(() -> new IllegalStateException("Algorithm catalog state row is missing"))
                .getCatalogVersion();
        var roots = algorithms.findAll();
        var rootsById = new LinkedHashMap<UUID, AlgorithmCatalogAlgorithmEntity>();
        roots.forEach(root -> rootsById.put(root.getId(), root));

        var disabledIds = new LinkedHashSet<UUID>();
        var trialPreferenceIds = new LinkedHashSet<UUID>();
        if (user != null && user.isMaintainer()) {
            disabledPreferences.findAllByIdUserId(user.id()).stream()
                    .map(preference -> preference.getId().getAlgorithmId())
                    .filter(rootsById::containsKey)
                    .sorted()
                    .forEach(disabledIds::add);
            trialPreferences.findAllByIdUserId(user.id()).stream()
                    .map(preference -> preference.getId().getRevisionId())
                    .sorted()
                    .forEach(trialPreferenceIds::add);
        }

        var wantedRevisionIds = new LinkedHashSet<UUID>();
        roots.stream().map(AlgorithmCatalogAlgorithmEntity::getActiveCanonicalRevisionId)
                .filter(java.util.Objects::nonNull).forEach(wantedRevisionIds::add);
        wantedRevisionIds.addAll(trialPreferenceIds);
        var revisionsById = new LinkedHashMap<UUID, AlgorithmCatalogRevisionEntity>();
        revisions.findAllByIdIn(wantedRevisionIds).forEach(revision -> revisionsById.put(revision.getId(), revision));

        var canonicalByRoot = new LinkedHashMap<UUID, AlgorithmCatalogRevisionEntity>();
        for (var root : roots) {
            var activeId = root.getActiveCanonicalRevisionId();
            var revision = activeId == null ? null : revisionsById.get(activeId);
            if (revision != null && root.getId().equals(revision.getAlgorithmId())
                    && "canonical".equals(revision.getStatus())) {
                canonicalByRoot.put(root.getId(), revision);
            }
        }

        var eligibleTrialsByRoot = new LinkedHashMap<UUID, List<AlgorithmCatalogRevisionEntity>>();
        for (var revisionId : trialPreferenceIds) {
            var revision = revisionsById.get(revisionId);
            if (revision == null) continue;
            var root = rootsById.get(revision.getAlgorithmId());
            if (root == null || !isEligibleTrial(revision, user)) continue;
            eligibleTrialsByRoot.computeIfAbsent(root.getId(), ignored -> new ArrayList<>()).add(revision);
        }

        var selectedByRoot = new LinkedHashMap<UUID, AlgorithmCatalogRevisionEntity>();
        canonicalByRoot.forEach((rootId, revision) -> {
            if (!disabledIds.contains(rootId)) selectedByRoot.put(rootId, revision);
        });
        eligibleTrialsByRoot.forEach((rootId, trials) -> {
            if (trials.size() == 1) selectedByRoot.put(rootId, trials.getFirst());
        });

        var orderedRoots = roots.stream().filter(root -> selectedByRoot.containsKey(root.getId()))
                .sorted(ROOT_ORDER).toList();
        var entries = new ArrayList<AlgorithmCaseCatalog.CatalogEntry>(orderedRoots.size());
        var identities = new ArrayList<SolverSnapshot.AlgorithmRevisionIdentity>(orderedRoots.size());
        for (var root : orderedRoots) {
            var revision = selectedByRoot.get(root.getId());
            var definition = decodeDefinition(revision.getDefinitionJson(), root, revision);
            entries.add(definition);
            identities.add(new SolverSnapshot.AlgorithmRevisionIdentity(root.getId(), revision.getId(),
                    revision.getRevisionNumber(), root.getPhase(), root.getName(), revision.getStatus()));
        }

        var solver = solverFor(identities, entries);
        var legacyNames = disabledIds.stream().map(rootsById::get)
                .filter(java.util.Objects::nonNull)
                .map(root -> root.getPhase() + ":" + root.getName())
                .sorted().toList();
        var enabledTrials = eligibleTrialsByRoot.entrySet().stream()
                .filter(entry -> entry.getValue().size() == 1)
                .map(entry -> entry.getValue().getFirst().getId()).sorted().toList();
        var manifest = manifest(version, user, identities, disabledIds, enabledTrials);
        return new SolverSnapshot(solver, version, identities, disabledIds.stream().sorted().toList(),
                enabledTrials, legacyNames, manifest);
    }

    private synchronized CfopSolveService solverFor(
            List<SolverSnapshot.AlgorithmRevisionIdentity> identities,
            List<AlgorithmCaseCatalog.CatalogEntry> entries
    ) {
        var fingerprint = identities.stream().map(SolverSnapshot.AlgorithmRevisionIdentity::revisionId).sorted().toList();
        var cached = solverContexts.get(fingerprint);
        if (cached != null) return cached;
        var dbs = AlgorithmDatabaseFactory.build(entries);
        var created = new CfopSolveService(dbs.setup(), dbs.insert(), dbs.oll(), dbs.pll());
        solverContexts.put(fingerprint, created);
        while (solverContexts.size() > MAX_CONTEXTS) {
            solverContexts.remove(solverContexts.keySet().iterator().next());
        }
        return created;
    }

    private boolean isEligibleTrial(AlgorithmCatalogRevisionEntity revision, AuthUser user) {
        if (user == null || !user.isMaintainer()) return false;
        if ("test-only".equals(revision.getStatus())) return true;
        return "experimental".equals(revision.getStatus())
                && revision.getAuthorUserId() != null
                && revision.getAuthorUserId() == user.id();
    }

    private AlgorithmCaseCatalog.CatalogEntry decodeDefinition(
            String json,
            AlgorithmCatalogAlgorithmEntity root,
            AlgorithmCatalogRevisionEntity revision
    ) {
        try {
            var node = mapper.readTree(json);
            var name = text(node, "name", root.getName());
            var phase = text(node, "phase", root.getPhase()).toLowerCase(java.util.Locale.ROOT);
            if (!root.getName().equals(name) || !root.getPhase().equals(phase)) {
                throw new IllegalStateException("Definition identity does not match its catalog algorithm");
            }
            var algorithm = requiredText(node, "algorithm");
            var slot = optionalSlot(node, "slot");
            var nonPreservedSlot = optionalSlot(node, "nonPreservedSlot");
            var sourceSetup = nullableText(node, "sourceSetup");
            var notes = nullableText(node, "notes");
            return new AlgorithmCaseCatalog.CatalogEntry(phase, name, slot, nonPreservedSlot,
                    null, null, algorithm, sourceSetup, sourceSetup, revision.getStatus(), notes);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Unable to decode algorithm revision " + revision.getId(), exception);
        }
    }

    private String manifest(
            long version,
            AuthUser user,
            List<SolverSnapshot.AlgorithmRevisionIdentity> identities,
            Collection<UUID> disabledIds,
            List<UUID> enabledTrials
    ) {
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("catalogVersion", version);
        manifest.put("role", user == null ? "anonymous" : user.role());
        manifest.put("algorithms", identities);
        manifest.put("disabledAlgorithmIds", disabledIds.stream().sorted().toList());
        manifest.put("enabledTrialRevisionIds", enabledTrials);
        try {
            return mapper.writeValueAsString(manifest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize solver provenance", exception);
        }
    }

    private static String text(tools.jackson.databind.JsonNode node, String field, String fallback) {
        var value = nullableText(node, field);
        return value == null ? fallback : value;
    }

    private static String requiredText(tools.jackson.databind.JsonNode node, String field) {
        var value = nullableText(node, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + field);
        return value;
    }

    private static String nullableText(tools.jackson.databind.JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static F2LSlot optionalSlot(tools.jackson.databind.JsonNode node, String field) {
        var value = nullableText(node, field);
        return value == null || value.isBlank() ? null : F2LSlot.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }

    private static final Comparator<AlgorithmCatalogAlgorithmEntity> ROOT_ORDER =
            Comparator.comparing((AlgorithmCatalogAlgorithmEntity root) -> root.getSeedOrder() == null ? 1 : 0)
                    .thenComparing(root -> root.getSeedOrder() == null ? Integer.MAX_VALUE : root.getSeedOrder())
                    .thenComparing(AlgorithmCatalogAlgorithmEntity::getPhase)
                    .thenComparing(AlgorithmCatalogAlgorithmEntity::getName);
}
