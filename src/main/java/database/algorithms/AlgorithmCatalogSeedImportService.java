package database.algorithms;

import algorithms.AlgorithmCaseCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Imports the bundled catalog once; subsequent application starts leave database state untouched. */
@Service
public class AlgorithmCatalogSeedImportService {
    private final AlgorithmCatalogStateJpaRepository states;
    private final AlgorithmCatalogAlgorithmJpaRepository algorithms;
    private final AlgorithmCatalogRevisionJpaRepository revisions;
    private final AlgorithmCatalogWorkflowEventJpaRepository events;
    private final JdbcTemplate jdbc;

    public AlgorithmCatalogSeedImportService(
            AlgorithmCatalogStateJpaRepository states,
            AlgorithmCatalogAlgorithmJpaRepository algorithms,
            AlgorithmCatalogRevisionJpaRepository revisions,
            AlgorithmCatalogWorkflowEventJpaRepository events,
            JdbcTemplate jdbc
    ) {
        this.states = states;
        this.algorithms = algorithms;
        this.revisions = revisions;
        this.events = events;
        this.jdbc = jdbc;
    }

    @Transactional
    public InitializationResult initializeSeedCatalog() {
        var state = states.findSingletonForUpdate()
                .orElseThrow(() -> new IllegalStateException("Algorithm catalog state row is missing"));
        List<AlgorithmCatalogSeed.SeedEntry> seed = AlgorithmCatalogSeed.entries();
        if (state.getInitializedAt() != null) {
            ensureLegacyAliases(seed);
            migrateLegacyDisabledProfiles(seed);
            return new InitializationResult(false, 0, state.getSeedVersion(), state.getSeedChecksum());
        }

        var checksum = AlgorithmCatalogSeed.checksum(seed);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        for (var item : seed) {
            if (algorithms.findBySeedKey(item.seedKey()).isPresent()) {
                throw new IllegalStateException(
                        "Algorithm seed records exist but the catalog initialization marker is empty: "
                                + item.seedKey()
                );
            }

            var algorithm = new AlgorithmCatalogAlgorithmEntity(
                    item.algorithmId(), item.phase(), item.name(), item.seedKey(), item.sourceOrder(), now
            );
            if ("canonical".equals(item.status())) {
                algorithm.setActiveCanonicalRevisionId(item.revisionId());
            }
            algorithms.save(algorithm);

            var revision = new AlgorithmCatalogRevisionEntity(
                    item.revisionId(), item.algorithmId(), 1, item.definitionJson(),
                    null, item.status(), now
            );
            revisions.save(revision);
            var importAction = "canonical".equals(item.status())
                    ? "seed-import-approved"
                    : "seed-imported";
            events.save(new AlgorithmCatalogWorkflowEventEntity(
                    importAction, item.revisionId(), null, item.status(),
                    "Imported from installation seed v" + AlgorithmCaseCatalog.VERSION, now
            ));
        }

        algorithms.flush();
        ensureLegacyAliases(seed);
        state.markSeedInitialized(AlgorithmCaseCatalog.VERSION, checksum, now);
        states.save(state);
        migrateLegacyDisabledProfiles(seed);
        return new InitializationResult(true, seed.size(), state.getSeedVersion(), state.getSeedChecksum());
    }

    private void ensureLegacyAliases(List<AlgorithmCatalogSeed.SeedEntry> seed) {
        seed.forEach(this::ensureLegacyAlias);
    }

    private void ensureLegacyAlias(AlgorithmCatalogSeed.SeedEntry item) {
        var alias = AlgorithmCaseCatalog.id(item.phase(), item.name());
        jdbc.update("""
                        INSERT INTO algorithm_catalog_legacy_aliases(alias, algorithm_id)
                        VALUES (?, ?)
                        ON CONFLICT (alias) DO NOTHING
                        """,
                alias, item.algorithmId());
        var mappedId = jdbc.queryForObject(
                "SELECT algorithm_id FROM algorithm_catalog_legacy_aliases WHERE alias = ?",
                java.util.UUID.class, alias);
        if (!item.algorithmId().equals(mappedId)) {
            throw new IllegalStateException("Legacy algorithm alias was reassigned: " + alias);
        }
    }

    private void migrateLegacyDisabledProfiles(List<AlgorithmCatalogSeed.SeedEntry> seed) {
        var aliases = new java.util.HashMap<String, java.util.UUID>();
        seed.forEach(item -> aliases.put(AlgorithmCaseCatalog.id(item.phase(), item.name()), item.algorithmId()));
        var legacy = jdbc.query("SELECT user_id, phase, case_name FROM user_disabled_algorithms ORDER BY user_id, phase, case_name",
                (resultSet, rowNumber) -> new LegacyDisabledPreference(
                        resultSet.getLong("user_id"),
                        AlgorithmCaseCatalog.id(resultSet.getString("phase"), resultSet.getString("case_name"))
                ));
        for (var preference : legacy) {
            var algorithmId = aliases.get(preference.alias());
            if (algorithmId == null) {
                throw new IllegalStateException("Cannot map saved solver profile algorithm alias: " + preference.alias());
            }
            jdbc.update("""
                            INSERT INTO user_disabled_algorithm_ids(user_id, algorithm_id)
                            VALUES (?, ?)
                            ON CONFLICT (user_id, algorithm_id) DO NOTHING
                            """,
                    preference.userId(), algorithmId);
        }
    }

    private record LegacyDisabledPreference(long userId, String alias) { }

    public record InitializationResult(
            boolean initializedNow,
            int importedCount,
            String seedVersion,
            String seedChecksum
    ) {
    }
}
