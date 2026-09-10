package server;

import database.persistence.entity.SpringHistoryPersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MVC adapter for authenticated solve statistics. */
@RestController
@RequestMapping("/api/stats")
final class SpringStatisticsController {
    private final SpringDatabaseHealth databaseHealth;
    private final SpringHistoryPersistenceService history;

    SpringStatisticsController(SpringDatabaseHealth databaseHealth, SpringHistoryPersistenceService history) {
        this.databaseHealth = databaseHealth;
        this.history = history;
    }

    @GetMapping
    ResponseEntity<String> stats() throws Exception {
        if (!databaseHealth.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
        var user = SpringRequestSupport.requireUser();
        return SpringRequestSupport.json(200, JsonSupport.solveStatisticsJson(
                history.statistics(user.externalId())));
    }
}
