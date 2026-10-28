package server.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import server.catalog.AlgorithmWorkspaceCatalogService;

/** MVC adapter for the maintainer workspace catalog endpoint. */
@RestController
@RequestMapping("/api/algorithms")
final class SpringAlgorithmController {
    private final SpringDatabaseHealth databaseHealth;
    private final AlgorithmWorkspaceCatalogService catalog;

    SpringAlgorithmController(SpringDatabaseHealth databaseHealth, AlgorithmWorkspaceCatalogService catalog) {
        this.databaseHealth = databaseHealth;
        this.catalog = catalog;
    }

    @GetMapping
    ResponseEntity<String> catalog(
            @RequestParam(value = "includeNonCanonical", required = false, defaultValue = "false") boolean includeNonCanonical,
            @RequestParam(value = "phase", required = false) String phase,
            @RequestParam(value = "slot", required = false) String slot,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "q", required = false, defaultValue = "") String search,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit
    ) {
        if (!databaseHealth.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
        var actor = SpringRequestSupport.requireMaintainer();
        var includeTrialStatuses = status != null && !status.isBlank()
                && !"canonical".equalsIgnoreCase(status) && !"all".equalsIgnoreCase(status);
        var page = catalog.list(actor, phase, slot, status, scope, search,
                includeNonCanonical || includeTrialStatuses, cursor, limit);
        return SpringRequestSupport.json(200,
                JsonSupport.algorithmWorkspaceCatalogJson(page));
    }
}
