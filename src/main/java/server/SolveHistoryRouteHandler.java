package server;

import api.CreateSolveAttemptRequest;
import api.SaveSolutionApiRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import database.CreateSolveAttemptCommand;
import database.DatabaseManager;
import database.SaveSolutionCommand;
import database.SolveHistoryRepository;

import java.io.IOException;

import static server.HttpServerSupport.handleCors;
import static server.HttpServerSupport.parseQuery;
import static server.HttpServerSupport.readJsonBody;
import static server.HttpServerSupport.requireAuthenticated;
import static server.HttpServerSupport.writeJson;

final class SolveHistoryRouteHandler implements HttpHandler {
    private final DatabaseManager databaseManager;
    private final SolveHistoryRepository repository;
    private final SolveJobManager solveJobManager;
    private final AuthService authService;

    SolveHistoryRouteHandler(
            DatabaseManager databaseManager,
            SolveJobManager solveJobManager,
            AuthService authService
    ) {
        this.databaseManager = databaseManager;
        this.repository = new SolveHistoryRepository(databaseManager);
        this.solveJobManager = solveJobManager;
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }
        if (!databaseManager.isConfigured()) {
            writeJson(exchange, 503, JsonSupport.errorJson("Database is not configured"));
            return;
        }

        try {
            var user = requireAuthenticated(exchange, authService);
            var pathParts = pathParts(exchange);
            if (pathParts.size() == 2 && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleCreateAttempt(exchange, user.externalId());
                return;
            }
            if (pathParts.size() == 2 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleList(exchange, user.externalId());
                return;
            }
            if (pathParts.size() == 3 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleDetail(exchange, user.externalId(), parseSolveId(pathParts.get(2)));
                return;
            }
            if (pathParts.size() == 3 && "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleDelete(exchange, user.externalId(), parseSolveId(pathParts.get(2)));
                return;
            }
            if (pathParts.size() == 5
                    && "solutions".equals(pathParts.get(3))
                    && "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleUpsertSolution(exchange, user.externalId(), parseSolveId(pathParts.get(2)), pathParts.get(4));
                return;
            }
            writeJson(exchange, 405, JsonSupport.errorJson("Method not allowed"));
        } catch (AuthService.UnauthorizedException exception) {
            writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
        } catch (Exception exception) {
            writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
        }
    }

    private void handleCreateAttempt(HttpExchange exchange, String userId) throws Exception {
        var body = readJsonBody(exchange);
        var request = new CreateSolveAttemptRequest(
                JsonSupport.readString(body, "clientAttemptId"),
                JsonSupport.readString(body, "scramble"),
                JsonSupport.readString(body, "crossFaceRequested"),
                JsonSupport.readInteger(body, "timerMs"),
                JsonSupport.readString(body, "penalty"),
                JsonSupport.readInteger(body, "officialMs"),
                JsonSupport.readBoolean(body, "dnf")
        );
        var saved = repository.createAttempt(new CreateSolveAttemptCommand(
                userId,
                request.clientAttemptId(),
                request.scramble(),
                request.crossFaceRequested(),
                request.timerMs(),
                request.penalty(),
                request.officialMs(),
                request.dnf()
        ));
        writeJson(exchange, 201, JsonSupport.solveHistoryEntryJson(saved));
    }

    private void handleUpsertSolution(
            HttpExchange exchange,
            String userId,
            long solveId,
            String mode
    ) throws Exception {
        if (!mode.equals("greedy") && !mode.equals("optimized")) {
            throw new IllegalArgumentException("Invalid F2L mode: " + mode);
        }
        var body = readJsonBody(exchange);
        var request = readSolutionRequest(body);
        if (!mode.equals(request.f2lMode())) {
            throw new IllegalArgumentException("Solution mode does not match request path");
        }
        var solution = combinedSolution(request);
        var saved = repository.upsertSolution(new SaveSolutionCommand(
                userId,
                solveId,
                mode,
                request.crossFaceRequested(),
                request.crossFaceChosen(),
                solution,
                solution,
                request.f2lSetupCaseCount(),
                request.f2lInsertCaseCount(),
                request.solvedF2LSlots(),
                request.totalMoves(),
                request.fullySolved(),
                request.solveElapsedMs(),
                request.crossAlgorithm(),
                request.crossMoves(),
                request.crossSolved(),
                request.crossStatus(),
                request.f2lAlgorithm(),
                request.f2lMoves(),
                request.f2lSolved(),
                request.f2lStatus(),
                request.ollAlgorithm(),
                request.ollMoves(),
                request.ollSolved(),
                request.ollStatus(),
                request.pllAlgorithm(),
                request.pllMoves(),
                request.pllSolved(),
                request.pllStatus(),
                DatabaseManager.SOLVER_VERSION,
                request.f2lTraceJson(),
                request.comparisonJson()
        ));
        writeJson(exchange, 200, JsonSupport.savedSolutionJson(saved));
    }

    private void handleList(HttpExchange exchange, String userId) throws Exception {
        var params = parseQuery(exchange.getRequestURI().getRawQuery());
        var limit = parseLimit(params.get("limit"));
        var cursor = database.HistoryCursor.parse(params.get("cursor"));
        var page = repository.listPage(userId, limit, cursor);
        writeJson(exchange, 200, JsonSupport.solveHistoryPageJson(page));
    }

    private void handleDetail(HttpExchange exchange, String userId, long solveId) throws Exception {
        writeJson(exchange, 200, JsonSupport.solveHistoryDetailJson(repository.findDetail(userId, solveId)));
    }

    private void handleDelete(HttpExchange exchange, String userId, long solveId) throws Exception {
        repository.requireOwnedSolve(userId, solveId);
        solveJobManager.cancelLinkedJobs(userId, solveId);
        repository.deleteSolve(userId, solveId);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return 20;
        }
        return Math.max(1, Math.min(Integer.parseInt(value.trim()), 100));
    }

    private static SaveSolutionApiRequest readSolutionRequest(String body) {
        return new SaveSolutionApiRequest(
                JsonSupport.readString(body, "crossFaceRequested"),
                JsonSupport.readString(body, "crossFaceChosen"),
                JsonSupport.readString(body, "f2lMode"),
                JsonSupport.readInteger(body, "f2lSetupCaseCount"),
                JsonSupport.readInteger(body, "f2lInsertCaseCount"),
                JsonSupport.readString(body, "solvedF2LSlots"),
                JsonSupport.readInteger(body, "totalMoves"),
                JsonSupport.readBoolean(body, "fullySolved"),
                JsonSupport.readDouble(body, "solveElapsedMs"),
                JsonSupport.readString(body, "crossAlgorithm"),
                JsonSupport.readInteger(body, "crossMoves"),
                JsonSupport.readBoolean(body, "crossSolved"),
                JsonSupport.readString(body, "crossStatus"),
                JsonSupport.readString(body, "f2lAlgorithm"),
                JsonSupport.readInteger(body, "f2lMoves"),
                JsonSupport.readBoolean(body, "f2lSolved"),
                JsonSupport.readString(body, "f2lStatus"),
                JsonSupport.readString(body, "ollAlgorithm"),
                JsonSupport.readInteger(body, "ollMoves"),
                JsonSupport.readBoolean(body, "ollSolved"),
                JsonSupport.readString(body, "ollStatus"),
                JsonSupport.readString(body, "pllAlgorithm"),
                JsonSupport.readInteger(body, "pllMoves"),
                JsonSupport.readBoolean(body, "pllSolved"),
                JsonSupport.readString(body, "pllStatus"),
                JsonSupport.readRawField(body, "f2lTraceJson"),
                JsonSupport.readRawField(body, "comparisonJson")
        );
    }

    private static String combinedSolution(SaveSolutionApiRequest request) {
        var builder = new StringBuilder();
        appendAlgorithm(builder, request.crossAlgorithm());
        appendAlgorithm(builder, request.f2lAlgorithm());
        appendAlgorithm(builder, request.ollAlgorithm());
        appendAlgorithm(builder, request.pllAlgorithm());
        return builder.toString().trim();
    }

    private static void appendAlgorithm(StringBuilder builder, String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(algorithm.trim());
    }

    private static long parseSolveId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid solve ID");
        }
    }

    private static java.util.List<String> pathParts(HttpExchange exchange) {
        return java.util.Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                .filter(part -> !part.isBlank())
                .toList();
    }
}
