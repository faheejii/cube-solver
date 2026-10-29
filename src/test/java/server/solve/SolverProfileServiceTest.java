package server.solve;

import algorithms.AlgorithmCaseCatalog;
import org.junit.jupiter.api.Test;
import solver.CfopSolveService;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolverProfileServiceTest {
    @Test
    void filteredCatalogs_shouldExcludeOnlyTheSelectedCanonicalCaseFromEveryPhase() {
        var ids = AlgorithmCaseCatalog.canonicalIds();
        for (var phase : List.of("setup", "insert", "oll", "pll")) {
            var id = ids.stream().filter(value -> value.startsWith(phase + ":")).findFirst().orElseThrow();
            var disabled = Set.of(id);
            switch (phase) {
                case "setup" -> assertTrue(AlgorithmCaseCatalog.setupDatabase(disabled).size()
                        < AlgorithmCaseCatalog.setupDatabase().size());
                case "insert" -> assertTrue(AlgorithmCaseCatalog.insertDatabase(disabled).size()
                        < AlgorithmCaseCatalog.insertDatabase().size());
                case "oll" -> assertTrue(AlgorithmCaseCatalog.ollDatabase(disabled).size()
                        < AlgorithmCaseCatalog.ollDatabase().size());
                case "pll" -> assertTrue(AlgorithmCaseCatalog.pllDatabase(disabled).size()
                        < AlgorithmCaseCatalog.pllDatabase().size());
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            }
        }
    }

    @Test
    void validate_shouldSortProfilesAndRejectUnknownOrDuplicateIds() {
        var ids = AlgorithmCaseCatalog.canonicalIds().stream().limit(2).toList();
        var service = new SolverProfileService(new CfopSolveService());

        assertEquals(ids.stream().sorted().toList(), service.validate(List.of(ids.get(1), ids.get(0))));
        assertThrows(IllegalArgumentException.class, () -> service.validate(List.of("pll:not-canonical")));
        assertThrows(IllegalArgumentException.class, () -> service.validate(List.of(ids.get(0), ids.get(0))));
    }

    @Test
    void solverFor_shouldReuseDefaultAndIdenticalProfilesWithoutMutatingDefault() {
        var defaultSolver = new CfopSolveService();
        var service = new SolverProfileService(defaultSolver);
        var id = AlgorithmCaseCatalog.canonicalIds().stream().filter(value -> value.startsWith("pll:"))
                .findFirst().orElseThrow();

        assertSame(defaultSolver, service.solverFor(List.of()));
        var filtered = service.solverFor(List.of(id));
        assertNotSame(defaultSolver, filtered);
        assertSame(filtered, service.solverFor(List.of(id)));
        assertSame(defaultSolver, service.solverFor(List.of()));
    }
}
