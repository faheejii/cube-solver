package algorithms;

import java.util.Collection;

/** Builds immutable phase indexes from validated catalog definitions selected for one solver snapshot. */
public final class AlgorithmDatabaseFactory {
    private AlgorithmDatabaseFactory() { }

    public static SolverDatabases build(Collection<AlgorithmCaseCatalog.CatalogEntry> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions cannot be null");
        }
        var setup = F2LSetupCaseDatabase.empty();
        var insert = F2LInsertCaseDatabase.empty();
        var oll = OLLCaseDatabase.empty();
        var pll = PLLCaseDatabase.empty();
        for (var definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("catalog definition cannot be null");
            }
            switch (definition.phase()) {
                case "setup" -> setup.register(definition.sourceSetup(), definition.algorithm(),
                        definition.nonPreservedSlot(), definition.name());
                case "insert" -> insert.register(definition.algorithm(), definition.slot(), definition.name());
                case "oll" -> oll.register(definition.algorithm(), definition.name());
                case "pll" -> pll.register(definition.algorithm(), definition.name());
                default -> throw new IllegalArgumentException("Unknown algorithm phase: " + definition.phase());
            }
        }
        setup.validate();
        insert.validate();
        oll.validate();
        pll.validate();
        return new SolverDatabases(setup, insert, oll, pll);
    }

    public record SolverDatabases(
            F2LSetupCaseDatabase setup,
            F2LInsertCaseDatabase insert,
            OLLCaseDatabase oll,
            PLLCaseDatabase pll
    ) { }
}
