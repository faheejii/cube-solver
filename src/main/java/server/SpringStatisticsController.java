package server;

import database.DatabaseManager;
import database.SolveHistoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MVC adapter for authenticated solve statistics. */
@RestController
@RequestMapping("/api/stats")
final class SpringStatisticsController {
    private final DatabaseManager databaseManager;
    private final SolveHistoryRepository repository;

    SpringStatisticsController(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.repository = new SolveHistoryRepository(databaseManager);
    }

    @GetMapping
    ResponseEntity<String> stats() throws Exception {
        if (!databaseManager.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
        var user = SpringRequestSupport.requireUser();
        return SpringRequestSupport.json(200, JsonSupport.solveStatisticsJson(
                repository.statistics(user.externalId())));
    }
}
