package database.algorithms;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlgorithmCatalogSeedImportServiceTest {
    @Test
    void importsSeedOnceAndDoesNotOverwriteInitializedCatalog() {
        var states = mock(AlgorithmCatalogStateJpaRepository.class);
        var algorithms = mock(AlgorithmCatalogAlgorithmJpaRepository.class);
        var revisions = mock(AlgorithmCatalogRevisionJpaRepository.class);
        var events = mock(AlgorithmCatalogWorkflowEventJpaRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var state = new AlgorithmCatalogStateEntity((short) 1);
        when(states.findSingletonForUpdate()).thenReturn(Optional.of(state));
        when(algorithms.findBySeedKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        when(jdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(java.util.UUID.class), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    var alias = invocation.getArgument(2, String.class);
                    return AlgorithmCatalogSeed.entries().stream()
                            .filter(entry -> (entry.phase() + ":" + entry.name()).equals(alias))
                            .findFirst().orElseThrow().algorithmId();
                });
        org.mockito.Mockito.doReturn(java.util.List.of()).when(jdbc).query(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class));

        var service = new AlgorithmCatalogSeedImportService(states, algorithms, revisions, events, jdbc);
        var imported = service.initializeSeedCatalog();
        var repeated = service.initializeSeedCatalog();

        assertTrue(imported.initializedNow());
        assertEquals(201, imported.importedCount());
        assertFalse(repeated.initializedNow());
        assertEquals(0, repeated.importedCount());
        assertEquals(imported.seedChecksum(), repeated.seedChecksum());
        verify(algorithms, times(201)).save(org.mockito.ArgumentMatchers.any(AlgorithmCatalogAlgorithmEntity.class));
        verify(revisions, times(201)).save(org.mockito.ArgumentMatchers.any(AlgorithmCatalogRevisionEntity.class));
        verify(events, times(201)).save(org.mockito.ArgumentMatchers.any(AlgorithmCatalogWorkflowEventEntity.class));
        verify(states, times(1)).save(state);
    }
}
