package server;

import algorithms.AlgorithmCaseCatalog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import database.DatabaseManager;

import java.io.IOException;

import static server.HttpServerSupport.handleCors;
import static server.HttpServerSupport.parseQuery;
import static server.HttpServerSupport.requireAdmin;
import static server.HttpServerSupport.writeJson;

/** Serves the admin-only canonical algorithm catalog and its F2L compatibility view. */
final class AlgorithmCatalogRouteHandler implements HttpHandler {
    private final DatabaseManager databaseManager;
    private final AuthService authService;

    AlgorithmCatalogRouteHandler(DatabaseManager databaseManager, AuthService authService) {
        this.databaseManager = databaseManager;
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, JsonSupport.errorJson("Method not allowed"));
            return;
        }
        if (!databaseManager.isConfigured()) {
            writeJson(exchange, 503, JsonSupport.errorJson("Database is not configured"));
            return;
        }
        try {
            requireAdmin(exchange, authService);
            var path = exchange.getRequestURI().getPath();
            var f2lOnly = path.endsWith("/f2l");
            if (!f2lOnly && !path.equals("/api/algorithms")) {
                writeJson(exchange, 404, JsonSupport.errorJson("Not found"));
                return;
            }
            var query = parseQuery(exchange.getRequestURI().getRawQuery());
            var includeNonCanonical = "true".equalsIgnoreCase(query.get("includeNonCanonical"));
            var phase = query.get("phase");
            var slot = query.get("slot");
            var status = query.get("status");
            var search = query.getOrDefault("q", "").toLowerCase(java.util.Locale.ROOT);
            var entries = AlgorithmCaseCatalog.entries(includeNonCanonical).stream()
                    .filter(entry -> !f2lOnly || "setup".equals(entry.phase()) || "insert".equals(entry.phase()))
                    .filter(entry -> phase == null || phase.isBlank() || entry.phase().equalsIgnoreCase(phase))
                    .filter(entry -> slot == null || slot.isBlank() || entry.slot().name().equalsIgnoreCase(slot))
                    .filter(entry -> status == null || status.isBlank() || "all".equalsIgnoreCase(status) || entry.status().equalsIgnoreCase(status))
                    .filter(entry -> search.isBlank()
                            || entry.name().toLowerCase(java.util.Locale.ROOT).contains(search)
                            || entry.algorithm().toLowerCase(java.util.Locale.ROOT).contains(search))
                    .toList();
            writeJson(exchange, 200, JsonSupport.algorithmCatalogJson(entries, AlgorithmCaseCatalog.VERSION));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
        } catch (AuthService.UnauthorizedException exception) {
            writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
        } catch (AuthService.ForbiddenException exception) {
            writeJson(exchange, 403, JsonSupport.errorJson(exception.getMessage()));
        } catch (Exception exception) {
            writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
        }
    }
}
