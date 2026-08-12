package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import database.DatabaseManager;
import database.SolveHistoryRepository;

import java.io.IOException;

import static server.HttpServerSupport.handleCors;
import static server.HttpServerSupport.requireAuthenticated;
import static server.HttpServerSupport.writeJson;

/** Serves authenticated solve statistics independently from HTTP server wiring. */
final class StatisticsRouteHandler implements HttpHandler {
    private final DatabaseManager databaseManager;
    private final SolveHistoryRepository repository;
    private final AuthService authService;

    StatisticsRouteHandler(DatabaseManager databaseManager, AuthService authService) {
        this.databaseManager = databaseManager;
        this.repository = new SolveHistoryRepository(databaseManager);
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
            var user = requireAuthenticated(exchange, authService);
            writeJson(exchange, 200, JsonSupport.solveStatisticsJson(repository.statistics(user.externalId())));
        } catch (AuthService.UnauthorizedException exception) {
            writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
        } catch (Exception exception) {
            writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
        }
    }
}
