package server.catalog;

import algorithms.AlgorithmCaseCatalog;
import api.AlgorithmDefinitionRequest;
import database.AuthUser;
import database.algorithms.AlgorithmCatalogAlgorithmEntity;
import database.algorithms.AlgorithmCatalogAlgorithmJpaRepository;
import database.algorithms.AlgorithmCatalogRevisionEntity;
import database.algorithms.AlgorithmCatalogRevisionJpaRepository;
import database.algorithms.AlgorithmCatalogStateJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlgorithmGovernanceServiceTest {
    private AlgorithmCatalogAlgorithmJpaRepository algorithms;
    private AlgorithmCatalogRevisionJpaRepository revisions;
    private AlgorithmCatalogStateJpaRepository states;
    private JdbcTemplate jdbc;
    private EntityManager entityManager;
    private AlgorithmGovernanceService service;

    @BeforeEach
    void setUp() {
        algorithms = mock(AlgorithmCatalogAlgorithmJpaRepository.class);
        revisions = mock(AlgorithmCatalogRevisionJpaRepository.class);
        states = mock(AlgorithmCatalogStateJpaRepository.class);
        jdbc = mock(JdbcTemplate.class);
        entityManager = mock(EntityManager.class);
        service = new AlgorithmGovernanceService(algorithms, revisions, states, jdbc, entityManager);
    }

    @Test
    void validatesSeedShapedSetupWithExistingCaseDatabaseValidators() {
        var source = AlgorithmCaseCatalog.sourceDefinitions().stream()
                .filter(definition -> "setup".equals(definition.phase())).findFirst().orElseThrow();
        var request = new AlgorithmDefinitionRequest(source.phase(), source.name(), source.algorithm(), source.notes(),
                null, source.nonPreservedSlot().name(), source.sourceSetup());

        var result = service.validate(request);

        assertEquals(true, result.valid());
        assertEquals("setup", result.phase());
        assertEquals(null, result.validatorNote());
    }

    @Test
    void givesCorrectionOrientedDiagnosticForMissingF2lInputs() {
        var request = new AlgorithmDefinitionRequest("insert", "example", "R U R'", null, null, null, null);

        var error = assertThrows(IllegalArgumentException.class, () -> service.validate(request));

        assertEquals(true, error.getMessage().contains("slot is required"));
        assertEquals(true, error.getMessage().contains("Check the move notation"));
    }

    @Test
    void rejectsLastLayerAlgorithmsThatOnlyPassInverseGeneratedValidation() {
        var oll = new AlgorithmDefinitionRequest("oll", "not-an-oll", "R U R'", null, null, null, null);
        var pll = new AlgorithmDefinitionRequest("pll", "not-a-pll", "R U R'", null, null, null, null);

        var ollError = assertThrows(IllegalArgumentException.class, () -> service.validate(oll));
        var pllError = assertThrows(IllegalArgumentException.class, () -> service.validate(pll));

        assertEquals(true, ollError.getMessage().contains("does not solve any known OLL case"));
        assertEquals(true, pllError.getMessage().contains("does not solve any known PLL case"));
    }

    @Test
    void privateExperimentalDetailsAreNotDisclosedToAnotherMaintainer() {
        var algorithmId = UUID.randomUUID();
        var algorithm = new AlgorithmCatalogAlgorithmEntity(algorithmId, "oll", "private", null, null, OffsetDateTime.now());
        var revision = new AlgorithmCatalogRevisionEntity(UUID.randomUUID(), algorithmId, 1,
                "{}", 2L, "experimental", OffsetDateTime.now());
        when(algorithms.findById(algorithmId)).thenReturn(Optional.of(algorithm));
        when(revisions.findAllByAlgorithmId(algorithmId)).thenReturn(List.of(revision));

        assertThrows(AlgorithmGovernanceService.NotFoundException.class,
                () -> service.detail(algorithmId, new AuthUser(1, "maintainer-1", null, null, "maintainer")));
    }

    @Test
    void nonAdminCannotApproveTestOnlyRevision() {
        var algorithmId = UUID.randomUUID();
        var revisionId = UUID.randomUUID();
        var algorithm = new AlgorithmCatalogAlgorithmEntity(algorithmId, "oll", "shared", null, null, OffsetDateTime.now());
        var revision = new AlgorithmCatalogRevisionEntity(revisionId, algorithmId, 1,
                "{}", 1L, "test-only", OffsetDateTime.now());
        when(states.findSingletonForUpdate()).thenReturn(Optional.of(mock(database.algorithms.AlgorithmCatalogStateEntity.class)));
        when(algorithms.findById(algorithmId)).thenReturn(Optional.of(algorithm));
        when(revisions.findById(revisionId)).thenReturn(Optional.of(revision));

        assertThrows(server.auth.AuthOperations.ForbiddenException.class,
                () -> service.act(algorithmId, revisionId, "approve", 0L, null,
                        new AuthUser(1, "maintainer-1", null, null, "maintainer")));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void experimentalRevisionCannotBeGloballyDeprecatedEvenByItsAuthor() {
        var algorithmId = UUID.randomUUID();
        var revisionId = UUID.randomUUID();
        var algorithm = new AlgorithmCatalogAlgorithmEntity(algorithmId, "oll", "private", null, null, OffsetDateTime.now());
        var revision = new AlgorithmCatalogRevisionEntity(revisionId, algorithmId, 1,
                "{}", 1L, "experimental", OffsetDateTime.now());
        when(states.findSingletonForUpdate()).thenReturn(Optional.of(mock(database.algorithms.AlgorithmCatalogStateEntity.class)));
        when(algorithms.findById(algorithmId)).thenReturn(Optional.of(algorithm));
        when(revisions.findById(revisionId)).thenReturn(Optional.of(revision));

        var error = assertThrows(AlgorithmGovernanceService.ConflictException.class,
                () -> service.act(algorithmId, revisionId, "deprecate", 0L, null,
                        new AuthUser(1, "maintainer-1", null, null, "maintainer")));

        assertEquals(true, error.getMessage().contains("personal"));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void maintainerCannotGloballyDeprecateSharedTestOnlyRevision() {
        var algorithmId = UUID.randomUUID();
        var revisionId = UUID.randomUUID();
        var algorithm = new AlgorithmCatalogAlgorithmEntity(algorithmId, "oll", "shared", null, null, OffsetDateTime.now());
        var revision = new AlgorithmCatalogRevisionEntity(revisionId, algorithmId, 1,
                "{}", 1L, "test-only", OffsetDateTime.now());
        when(states.findSingletonForUpdate()).thenReturn(Optional.of(mock(database.algorithms.AlgorithmCatalogStateEntity.class)));
        when(algorithms.findById(algorithmId)).thenReturn(Optional.of(algorithm));
        when(revisions.findById(revisionId)).thenReturn(Optional.of(revision));

        assertThrows(server.auth.AuthOperations.ForbiddenException.class,
                () -> service.act(algorithmId, revisionId, "deprecate", 0L, null,
                        new AuthUser(1, "maintainer-1", null, null, "maintainer")));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void staleExpectedVersionConflictsBeforeAnyStatusMutation() {
        var algorithmId = UUID.randomUUID();
        var revisionId = UUID.randomUUID();
        var algorithm = new AlgorithmCatalogAlgorithmEntity(algorithmId, "oll", "shared", null, null, OffsetDateTime.now());
        var revision = new AlgorithmCatalogRevisionEntity(revisionId, algorithmId, 3,
                "{}", 1L, "test-only", OffsetDateTime.now());
        when(states.findSingletonForUpdate()).thenReturn(Optional.of(mock(database.algorithms.AlgorithmCatalogStateEntity.class)));
        when(algorithms.findById(algorithmId)).thenReturn(Optional.of(algorithm));
        when(revisions.findById(revisionId)).thenReturn(Optional.of(revision));

        assertThrows(AlgorithmGovernanceService.ConflictException.class,
                () -> service.act(algorithmId, revisionId, "approve", 7L, null,
                        new AuthUser(9, "admin-9", null, null, "admin")));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }
}
