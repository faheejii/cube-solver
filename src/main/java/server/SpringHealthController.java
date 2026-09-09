package server;

import database.DatabaseHealth;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.SQLException;

/** Preserves the existing liveness, readiness, and process metrics response shapes. */
@RestController
@RequestMapping("/api")
final class SpringHealthController {
    private final SpringDatabaseHealth databaseHealth;
    private final OperationalMetrics operationalMetrics;

    SpringHealthController(SpringDatabaseHealth databaseHealth, OperationalMetrics operationalMetrics) {
        this.databaseHealth = databaseHealth;
        this.operationalMetrics = operationalMetrics;
    }

    @GetMapping("/health/live")
    ResponseEntity<String> live() {
        return SpringRequestSupport.json(200, JsonSupport.livenessJson());
    }

    @GetMapping("/health/ready")
    ResponseEntity<String> ready() {
        var health = databaseHealth.check();
        return SpringRequestSupport.json(
                "error".equals(health.status()) ? 503 : 200,
                JsonSupport.healthJson(health));
    }

    @GetMapping("/metrics")
    ResponseEntity<String> metrics() {
        return SpringRequestSupport.json(200, operationalMetrics.json());
    }
}

/** Spring-owned readiness probe backed by Boot's DataSource when configured. */
final class SpringDatabaseHealth {
    private final DataSource dataSource;

    SpringDatabaseHealth(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    static SpringDatabaseHealth from(ObjectProvider<DataSource> dataSources) {
        return new SpringDatabaseHealth(dataSources.getIfAvailable());
    }

    DatabaseHealth check() {
        if (dataSource == null) {
            return DatabaseHealth.disabled();
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return DatabaseHealth.ok();
        } catch (SQLException | RuntimeException exception) {
            var message = exception.getMessage();
            return DatabaseHealth.error(message == null || message.isBlank()
                    ? exception.getClass().getSimpleName()
                    : message);
        }
    }
}
