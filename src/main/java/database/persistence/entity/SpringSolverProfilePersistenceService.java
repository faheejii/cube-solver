package database.persistence.entity;

import database.persistence.repository.UserJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import server.solve.SolverProfileOperations;
import server.solve.SolverProfileOperations.Profile;
import database.algorithms.AlgorithmCatalogAlgorithmJpaRepository;
import database.algorithms.AlgorithmCatalogRevisionJpaRepository;
import database.algorithms.UserDisabledAlgorithmJpaRepository;
import database.algorithms.UserEnabledTrialRevisionJpaRepository;

import java.util.List;
import java.util.UUID;

/** Stores personal disable preferences by stable catalog identity, with legacy-name API aliases. */
@Service
public class SpringSolverProfilePersistenceService implements SolverProfileOperations {
    private final UserJpaRepository users;
    private final JdbcTemplate jdbc;
    private final AlgorithmCatalogAlgorithmJpaRepository algorithms;
    private final AlgorithmCatalogRevisionJpaRepository revisions;
    private final UserDisabledAlgorithmJpaRepository disabledPreferences;
    private final UserEnabledTrialRevisionJpaRepository trialPreferences;

    public SpringSolverProfilePersistenceService(
            UserJpaRepository users,
            JdbcTemplate jdbc,
            AlgorithmCatalogAlgorithmJpaRepository algorithms,
            AlgorithmCatalogRevisionJpaRepository revisions,
            UserDisabledAlgorithmJpaRepository disabledPreferences,
            UserEnabledTrialRevisionJpaRepository trialPreferences
    ) {
        this.users = users;
        this.jdbc = jdbc;
        this.algorithms = algorithms;
        this.revisions = revisions;
        this.disabledPreferences = disabledPreferences;
        this.trialPreferences = trialPreferences;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findDisabledAlgorithms(String userExternalId) {
        var user = requireUser(userExternalId);
        return jdbc.query("""
                        SELECT aliases.alias
                        FROM user_disabled_algorithm_ids disabled
                        JOIN algorithm_catalog_legacy_aliases aliases
                          ON aliases.algorithm_id = disabled.algorithm_id
                        WHERE disabled.user_id = ?
                        ORDER BY aliases.alias
                        """,
                (row, index) -> row.getString("alias"), user.getId());
    }

    @Override
    @Transactional
    public List<String> replaceDisabledAlgorithms(String userExternalId, List<String> disabledAlgorithms) {
        return replaceProfile(userExternalId, disabledAlgorithms, null, null).disabledAlgorithms();
    }

    @Override
    @Transactional(readOnly = true)
    public Profile findProfile(String userExternalId) {
        var user = requireUser(userExternalId);
        var ids = disabledPreferences.findAllByIdUserId(user.getId()).stream()
                .map(preference -> preference.getId().getAlgorithmId()).sorted().toList();
        var aliases = findAliases(user.getId());
        var trials = trialPreferences.findAllByIdUserId(user.getId()).stream()
                .map(preference -> preference.getId().getRevisionId()).sorted().toList();
        return new Profile(aliases, ids, trials, inactiveTrials(trials, user.getId()));
    }

    @Override
    @Transactional
    public Profile replaceProfile(String userExternalId, List<String> legacyAliases,
                                  List<UUID> disabledIds, List<UUID> enabledTrialIds) {
        if (legacyAliases != null && disabledIds != null) {
            throw new IllegalArgumentException("Use either disabledAlgorithms or disabledAlgorithmIds, not both");
        }
        var user = requireUser(userExternalId);
        var currentDisabled = disabledPreferences.findAllByIdUserId(user.getId()).stream()
                .map(preference -> preference.getId().getAlgorithmId()).toList();
        var desiredDisabled = legacyAliases == null && disabledIds == null ? currentDisabled
                : legacyAliases != null ? resolveLegacyAliases(legacyAliases) : List.copyOf(disabledIds);
        validateCanonicalIds(desiredDisabled);

        var currentTrials = trialPreferences.findAllByIdUserId(user.getId()).stream()
                .map(preference -> preference.getId().getRevisionId()).toList();
        var desiredTrials = enabledTrialIds == null ? currentTrials : List.copyOf(enabledTrialIds);
        validateTrials(desiredTrials, user.getId(), currentTrials);

        jdbc.update("DELETE FROM user_disabled_algorithm_ids WHERE user_id = ?", user.getId());
        for (var algorithmId : desiredDisabled) {
            jdbc.update("INSERT INTO user_disabled_algorithm_ids (user_id, algorithm_id) VALUES (?, ?)", user.getId(), algorithmId);
        }
        if (enabledTrialIds != null) {
            jdbc.update("DELETE FROM user_enabled_trial_revision_ids WHERE user_id = ?", user.getId());
            for (var revisionId : desiredTrials) {
                jdbc.update("INSERT INTO user_enabled_trial_revision_ids (user_id, revision_id) VALUES (?, ?)", user.getId(), revisionId);
            }
        }
        var savedTrials = desiredTrials.stream().sorted().toList();
        return new Profile(findAliases(user.getId()), desiredDisabled.stream().sorted().toList(), savedTrials,
                inactiveTrials(savedTrials, user.getId()));
    }

    private List<UUID> resolveLegacyAliases(List<String> aliases) {
        var resolved = new java.util.LinkedHashSet<UUID>();
        for (var alias : aliases) {
            if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Disabled algorithm alias cannot be blank");
            var id = jdbc.query("SELECT algorithm_id FROM algorithm_catalog_legacy_aliases WHERE alias = ?",
                    (row, index) -> row.getObject(1, UUID.class), alias.trim()).stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown legacy algorithm: " + alias));
            if (!resolved.add(id)) throw new IllegalArgumentException("Duplicate disabled algorithm: " + alias);
        }
        return List.copyOf(resolved);
    }

    private void validateCanonicalIds(List<UUID> ids) {
        var unique = new java.util.HashSet<UUID>();
        var roots = algorithms.findAllByIdIn(ids);
        var byId = new java.util.HashMap<UUID, database.algorithms.AlgorithmCatalogAlgorithmEntity>();
        roots.forEach(root -> byId.put(root.getId(), root));
        if (byId.size() != ids.size()) throw new IllegalArgumentException("Unknown disabled algorithm identity");
        for (var id : ids) {
            if (!unique.add(id)) throw new IllegalArgumentException("Duplicate disabled algorithm identity: " + id);
            var root = byId.get(id);
            if (root.getActiveCanonicalRevisionId() == null || !Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM algorithm_catalog_revisions
                        WHERE id = ? AND algorithm_id = ? AND status = 'canonical')
                    """, Boolean.class, root.getActiveCanonicalRevisionId(), id))) {
                throw new IllegalArgumentException("Only currently canonical algorithms can be disabled: " + id);
            }
        }
    }

    private void validateTrials(List<UUID> ids, long userId, List<UUID> currentIds) {
        var unique = new java.util.HashSet<UUID>();
        var algorithmIds = new java.util.HashSet<UUID>();
        var current = java.util.Set.copyOf(currentIds);
        for (var id : ids) {
            if (!unique.add(id)) throw new IllegalArgumentException("Duplicate enabled trial revision: " + id);
            var revision = revisions.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown trial revision: " + id));
            var eligible = "test-only".equals(revision.getStatus())
                    || "experimental".equals(revision.getStatus()) && revision.getAuthorUserId() != null
                    && revision.getAuthorUserId() == userId;
            if (!eligible && !current.contains(id)) {
                throw new IllegalArgumentException("This revision is not eligible for your solver profile: " + id);
            }
            if (!algorithmIds.add(revision.getAlgorithmId())) {
                throw new IllegalArgumentException("Enable at most one trial revision per algorithm");
            }
        }
    }

    private List<UUID> inactiveTrials(List<UUID> ids, long userId) {
        return revisions.findAllByIdIn(ids).stream()
                .filter(revision -> !("test-only".equals(revision.getStatus())
                        || "experimental".equals(revision.getStatus())
                        && revision.getAuthorUserId() != null && revision.getAuthorUserId() == userId))
                .map(database.algorithms.AlgorithmCatalogRevisionEntity::getId)
                .sorted().toList();
    }

    private List<String> findAliases(long userId) {
        return jdbc.query("""
                        SELECT aliases.alias
                        FROM user_disabled_algorithm_ids disabled
                        JOIN algorithm_catalog_legacy_aliases aliases ON aliases.algorithm_id = disabled.algorithm_id
                        WHERE disabled.user_id = ? ORDER BY aliases.alias
                        """,
                (row, index) -> row.getString("alias"), userId);
    }

    private UserEntity requireUser(String externalId) {
        return users.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
