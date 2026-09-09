package server;

import database.DatabaseManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preserves the existing liveness, readiness, and process metrics response shapes. */
@RestController
@RequestMapping("/api")
final class SpringHealthController {
    private final DatabaseManager databaseManager;
    private final OperationalMetrics operationalMetrics;

    SpringHealthController(DatabaseManager databaseManager, OperationalMetrics operationalMetrics) {
        this.databaseManager = databaseManager;
        this.operationalMetrics = operationalMetrics;
    }

    @GetMapping("/health/live")
    ResponseEntity<String> live() {
        return SpringRequestSupport.json(200, JsonSupport.livenessJson());
    }

    @GetMapping("/health/ready")
    ResponseEntity<String> ready() {
        var health = databaseManager.health();
        return SpringRequestSupport.json(
                "error".equals(health.status()) ? 503 : 200,
                JsonSupport.healthJson(health));
    }

    @GetMapping("/metrics")
    ResponseEntity<String> metrics() {
        return SpringRequestSupport.json(200, operationalMetrics.json());
    }
}
