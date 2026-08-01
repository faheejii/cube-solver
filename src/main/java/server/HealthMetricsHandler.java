package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import database.DatabaseManager;

import java.io.IOException;

/** Serves process liveness, database readiness, and operational metrics. */
final class HealthMetricsHandler implements HttpHandler {
    private final DatabaseManager databaseManager;
    private final OperationalMetrics operationalMetrics;

    HealthMetricsHandler(DatabaseManager databaseManager, OperationalMetrics operationalMetrics) {
        this.databaseManager = databaseManager;
        this.operationalMetrics = operationalMetrics;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var path = exchange.getRequestURI().getPath();
        if (path.endsWith("/live")) {
            ApiResponses.writeJson(exchange, 200, JsonSupport.livenessJson());
            return;
        }
        if (path.endsWith("/metrics")) {
            ApiResponses.writeJson(exchange, 200, operationalMetrics.json());
            return;
        }

        var health = databaseManager.health();
        var status = "error".equals(health.status()) ? 503 : 200;
        ApiResponses.writeJson(exchange, status, JsonSupport.healthJson(health));
    }
}
