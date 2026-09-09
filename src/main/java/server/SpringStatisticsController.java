package server;

import database.DatabaseManager;
import database.persistence.entity.SpringHistoryPersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MVC adapter for authenticated solve statistics. */
@RestController
@RequestMapping("/api/stats")
final class SpringStatisticsController {
    private final DatabaseManager databaseManager;
    private final SpringHistoryPersistenceService history;

    SpringStatisticsController(DatabaseManager databaseManager, SpringHistoryPersistenceService history) {
        this.databaseManager = databaseManager;
        this.history = history;
    }

    @GetMapping
    ResponseEntity<String> stats() throws Exception {
        if (!databaseManager.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
        var user = SpringRequestSupport.requireUser();
        return SpringRequestSupport.json(200, JsonSupport.solveStatisticsJson(
                history.statistics(user.externalId())));
    }
}
