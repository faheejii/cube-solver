package server;

import algorithms.AlgorithmCaseCatalog;
import database.DatabaseManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/** MVC adapter for the admin-only algorithm catalog endpoint. */
@RestController
@RequestMapping("/api/algorithms")
final class SpringAlgorithmController {
    private final DatabaseManager databaseManager;

    SpringAlgorithmController(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @GetMapping
    ResponseEntity<String> catalog(
            @RequestParam(value = "includeNonCanonical", required = false, defaultValue = "false") boolean includeNonCanonical,
            @RequestParam(value = "phase", required = false) String phase,
            @RequestParam(value = "slot", required = false) String slot,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false, defaultValue = "") String search
    ) {
        if (!databaseManager.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
        SpringRequestSupport.requireAdmin();
        var normalizedSearch = search.toLowerCase(Locale.ROOT);
        var entries = AlgorithmCaseCatalog.entries(includeNonCanonical).stream()
                .filter(entry -> phase == null || phase.isBlank() || entry.phase().equalsIgnoreCase(phase))
                .filter(entry -> slot == null || slot.isBlank()
                        || (entry.slot() != null && entry.slot().name().equalsIgnoreCase(slot))
                        || (entry.nonPreservedSlot() != null && entry.nonPreservedSlot().name().equalsIgnoreCase(slot)))
                .filter(entry -> status == null || status.isBlank() || "all".equalsIgnoreCase(status)
                        || entry.status().equalsIgnoreCase(status))
                .filter(entry -> normalizedSearch.isBlank()
                        || entry.name().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                        || entry.algorithm().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .toList();
        return SpringRequestSupport.json(200,
                JsonSupport.algorithmCatalogJson(entries, AlgorithmCaseCatalog.VERSION));
    }
}
