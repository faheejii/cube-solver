package server.solve;

import algorithms.AlgorithmCaseCatalog;
import database.AuthUser;
import database.algorithms.AlgorithmCatalogAlgorithmEntity;
import database.algorithms.AlgorithmCatalogAlgorithmJpaRepository;
import database.algorithms.AlgorithmCatalogRevisionEntity;
import database.algorithms.AlgorithmCatalogRevisionJpaRepository;
import database.algorithms.AlgorithmCatalogStateEntity;
import database.algorithms.AlgorithmCatalogStateJpaRepository;
import database.algorithms.UserAlgorithmPreferenceId;
import database.algorithms.UserDisabledAlgorithmEntity;
import database.algorithms.UserDisabledAlgorithmJpaRepository;
import database.algorithms.UserEnabledTrialRevisionEntity;
import database.algorithms.UserEnabledTrialRevisionJpaRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseSolverSnapshotProviderTest {
    private final AlgorithmCatalogAlgorithmJpaRepository algorithms = mock(AlgorithmCatalogAlgorithmJpaRepository.class);
    private final AlgorithmCatalogRevisionJpaRepository revisions = mock(AlgorithmCatalogRevisionJpaRepository.class);
    private final AlgorithmCatalogStateJpaRepository states = mock(AlgorithmCatalogStateJpaRepository.class);
    private final UserDisabledAlgorithmJpaRepository disabled = mock(UserDisabledAlgorithmJpaRepository.class);
    private final UserEnabledTrialRevisionJpaRepository trials = mock(UserEnabledTrialRevisionJpaRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void regularUsersReceiveOnlyActiveCanonicalAndDoNotReadPersonalPreferences() throws Exception {
        var definition = seedOll();
        var rootId = UUID.randomUUID();
        var canonicalId = UUID.randomUUID();
        var root = root(rootId, "oll", definition.name(), canonicalId);
        var canonical = revision(canonicalId, rootId, definition, "canonical", null);
        givenCatalog(root, canonical);

        var snapshot = provider().capture(new AuthUser(11, "u11", "user@example.test", "User", "user"));

        assertEquals(1L, snapshot.catalogVersion());
        assertEquals(List.of(canonicalId), snapshot.algorithms().stream()
                .map(SolverSnapshot.AlgorithmRevisionIdentity::revisionId).toList());
        assertTrue(snapshot.legacyDisabledAlgorithms().isEmpty());
        verify(disabled, never()).findAllByIdUserId(11);
        verify(trials, never()).findAllByIdUserId(11);
    }

    @Test
    void maintainerTrialReplacesItsCanonicalAndCapturesDisabledNamesAndManifest() throws Exception {
        var definition = seedOll();
        var rootId = UUID.randomUUID();
        var canonicalId = UUID.randomUUID();
        var trialId = UUID.randomUUID();
        var root = root(rootId, "oll", definition.name(), canonicalId);
        var canonical = revision(canonicalId, rootId, definition, "canonical", null);
        var trial = revision(trialId, rootId, definition, "test-only", 42L);
        givenCatalog(root, canonical, trial);
        when(disabled.findAllByIdUserId(42L)).thenReturn(List.of(
                new UserDisabledAlgorithmEntity(new UserAlgorithmPreferenceId(42L, rootId), OffsetDateTime.now())));
        when(trials.findAllByIdUserId(42L)).thenReturn(List.of(
                new UserEnabledTrialRevisionEntity(new database.algorithms.UserTrialRevisionPreferenceId(42L, trialId),
                        OffsetDateTime.now())));

        var snapshot = provider().capture(new AuthUser(42, "m42", "maintainer@example.test", "Maintainer", "maintainer"));

        assertEquals(List.of(trialId), snapshot.algorithms().stream()
                .map(SolverSnapshot.AlgorithmRevisionIdentity::revisionId).toList());
        assertEquals(List.of(rootId), snapshot.disabledAlgorithmIds());
        assertEquals(List.of(trialId), snapshot.enabledTrialRevisionIds());
        assertEquals(List.of("oll:" + definition.name()), snapshot.legacyDisabledAlgorithms());
        assertTrue(snapshot.manifestJson().contains("\"catalogVersion\":1"));
        assertTrue(snapshot.manifestJson().contains(trialId.toString()));
    }

    @Test
    void experimentalTrialAuthoredByAnotherMaintainerIsExcluded() throws Exception {
        var definition = seedOll();
        var rootId = UUID.randomUUID();
        var canonicalId = UUID.randomUUID();
        var trialId = UUID.randomUUID();
        var root = root(rootId, "oll", definition.name(), canonicalId);
        var canonical = revision(canonicalId, rootId, definition, "canonical", null);
        var trial = revision(trialId, rootId, definition, "experimental", 99L);
        givenCatalog(root, canonical, trial);
        when(trials.findAllByIdUserId(42L)).thenReturn(List.of(
                new UserEnabledTrialRevisionEntity(new database.algorithms.UserTrialRevisionPreferenceId(42L, trialId),
                        OffsetDateTime.now())));

        var snapshot = provider().capture(new AuthUser(42, "m42", "maintainer@example.test", "Maintainer", "maintainer"));

        assertEquals(List.of(canonicalId), snapshot.algorithms().stream()
                .map(SolverSnapshot.AlgorithmRevisionIdentity::revisionId).toList());
        assertTrue(snapshot.enabledTrialRevisionIds().isEmpty());
    }

    @Test
    void identicalEffectiveRevisionSetReusesImmutableSolverContextAcrossCatalogVersions() throws Exception {
        var definition = seedOll();
        var rootId = UUID.randomUUID();
        var canonicalId = UUID.randomUUID();
        var root = root(rootId, "oll", definition.name(), canonicalId);
        var canonical = revision(canonicalId, rootId, definition, "canonical", null);
        givenCatalog(root, canonical);
        var provider = provider();
        var first = provider.capture(null);
        var newerState = state(2);
        when(states.findById((short) 1)).thenReturn(java.util.Optional.of(newerState));
        var second = provider.capture(null);

        assertEquals(2L, second.catalogVersion());
        assertTrue(first.algorithms().equals(second.algorithms()));
        assertSame(first.solver(), second.solver());
    }

    private DatabaseSolverSnapshotProvider provider() {
        return new DatabaseSolverSnapshotProvider(algorithms, revisions, states, disabled, trials, mapper);
    }

    private void givenCatalog(AlgorithmCatalogAlgorithmEntity root, AlgorithmCatalogRevisionEntity... catalogRevisions) {
        var initialState = state(1);
        when(states.findById((short) 1)).thenReturn(java.util.Optional.of(initialState));
        when(algorithms.findAll()).thenReturn(List.of(root));
        when(revisions.findAllByIdIn(anyCollection())).thenAnswer(invocation -> {
            var ids = (java.util.Collection<UUID>) invocation.getArgument(0);
            return java.util.Arrays.stream(catalogRevisions).filter(revision -> ids.contains(revision.getId())).toList();
        });
    }

    private AlgorithmCaseCatalog.CatalogEntry seedOll() {
        return AlgorithmCaseCatalog.entries(true).stream().filter(entry -> "oll".equals(entry.phase()))
                .findFirst().orElseThrow();
    }

    private AlgorithmCatalogAlgorithmEntity root(UUID id, String phase, String name, UUID canonicalRevisionId) {
        var root = new AlgorithmCatalogAlgorithmEntity(id, phase, name, null, null, OffsetDateTime.now());
        root.setActiveCanonicalRevisionId(canonicalRevisionId);
        return root;
    }

    private AlgorithmCatalogRevisionEntity revision(UUID id, UUID rootId, AlgorithmCaseCatalog.CatalogEntry entry,
                                                     String status, Long authorId) throws Exception {
        var definition = new java.util.LinkedHashMap<String, Object>();
        definition.put("phase", entry.phase());
        definition.put("name", entry.name());
        definition.put("algorithm", entry.algorithm());
        definition.put("notes", entry.notes());
        definition.put("slot", entry.slot() == null ? null : entry.slot().name());
        definition.put("nonPreservedSlot", entry.nonPreservedSlot() == null ? null : entry.nonPreservedSlot().name());
        definition.put("sourceSetup", entry.sourceSetup());
        return new AlgorithmCatalogRevisionEntity(id, rootId, 1, mapper.writeValueAsString(definition), authorId,
                status, OffsetDateTime.now());
    }

    private static AlgorithmCatalogStateEntity state(long version) {
        var state = mock(AlgorithmCatalogStateEntity.class);
        when(state.getCatalogVersion()).thenReturn(version);
        return state;
    }
}
