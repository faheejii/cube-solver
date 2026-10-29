package database.algorithms;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Ensures a fresh database has its initial catalog before the application begins serving requests. */
@Component
public class AlgorithmCatalogSeedImportRunner implements ApplicationRunner {
    private final AlgorithmCatalogSeedImportService seedImport;

    public AlgorithmCatalogSeedImportRunner(AlgorithmCatalogSeedImportService seedImport) {
        this.seedImport = seedImport;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedImport.initializeSeedCatalog();
    }
}
