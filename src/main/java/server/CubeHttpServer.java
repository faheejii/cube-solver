package server;

import api.CreateSolveAttemptRequest;
import api.SaveSolutionApiRequest;
import api.SolveApiRequest;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import database.DatabaseManager;
import database.CreateSolveAttemptCommand;
import database.SaveSolutionCommand;
import database.SolveHistoryRepository;
import solver.CfopSolveService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public class CubeHttpServer {
    private final CfopSolveService solveService;
    private final Path frontendDistDir;
    private final DatabaseManager databaseManager;

    public CubeHttpServer(CfopSolveService solveService, Path frontendDistDir, DatabaseManager databaseManager) {
        this.solveService = solveService;
        this.frontendDistDir = frontendDistDir;
        this.databaseManager = databaseManager;
    }

    public HttpServer create(int port) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", exchange -> writeJson(exchange, 200, JsonSupport.healthJson(databaseManager.health())));
        server.createContext("/api/solve", new SolveHandler(solveService));
        server.createContext("/api/solves", new SolveHistoryHandler(databaseManager));
        server.createContext("/", new StaticFileHandler(frontendDistDir));
        server.setExecutor(Executors.newCachedThreadPool());
        return server;
    }

    private static final class SolveHandler implements HttpHandler {
        private final CfopSolveService solveService;

        private SolveHandler(CfopSolveService solveService) {
            this.solveService = solveService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, JsonSupport.errorJson("Method not allowed"));
                return;
            }

            try {
                var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var request = new SolveApiRequest(
                        JsonSupport.readString(body, "scramble"),
                        JsonSupport.readString(body, "crossFace"),
                        JsonSupport.readString(body, "f2lMode")
                );
                var result = solveService.solve(request.toSolveRequest());
                writeJson(exchange, 200, JsonSupport.solveResultJson(result));
            } catch (IllegalArgumentException exception) {
                writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
            } catch (Exception exception) {
                writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
            }
        }
    }

    private static final class SolveHistoryHandler implements HttpHandler {
        private final DatabaseManager databaseManager;
        private final SolveHistoryRepository repository;

        private SolveHistoryHandler(DatabaseManager databaseManager) {
            this.databaseManager = databaseManager;
            this.repository = new SolveHistoryRepository(databaseManager);
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
                var pathParts = pathParts(exchange);
                if (pathParts.size() == 2 && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreateAttempt(exchange);
                    return;
                }
                if (pathParts.size() == 2 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleList(exchange);
                    return;
                }
                if (pathParts.size() == 3 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleDetail(exchange, parseSolveId(pathParts.get(2)));
                    return;
                }
                if (pathParts.size() == 5
                        && "solutions".equals(pathParts.get(3))
                        && "PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleUpsertSolution(exchange, parseSolveId(pathParts.get(2)), pathParts.get(4));
                    return;
                }
                writeJson(exchange, 405, JsonSupport.errorJson("Method not allowed"));
            } catch (IllegalArgumentException exception) {
                writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
            } catch (Exception exception) {
                writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
            }
        }

        private void handleCreateAttempt(HttpExchange exchange) throws Exception {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var request = new CreateSolveAttemptRequest(
                    JsonSupport.readString(body, "userId"),
                    JsonSupport.readString(body, "clientAttemptId"),
                    JsonSupport.readString(body, "scramble"),
                    JsonSupport.readString(body, "crossFaceRequested"),
                    JsonSupport.readInteger(body, "timerMs"),
                    JsonSupport.readString(body, "penalty"),
                    JsonSupport.readInteger(body, "officialMs"),
                    JsonSupport.readBoolean(body, "dnf")
            );
            var saved = repository.createAttempt(new CreateSolveAttemptCommand(
                    request.userId(),
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

        private void handleUpsertSolution(HttpExchange exchange, long solveId, String mode) throws Exception {
            if (!mode.equals("greedy") && !mode.equals("optimized")) {
                throw new IllegalArgumentException("Invalid F2L mode: " + mode);
            }
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var request = readSolutionRequest(body);
            if (!mode.equals(request.f2lMode())) {
                throw new IllegalArgumentException("Solution mode does not match request path");
            }
            var solution = combinedSolution(request);
            var saved = repository.upsertSolution(new SaveSolutionCommand(
                    request.userId(),
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
                    DatabaseManager.SOLVER_VERSION
            ));
            writeJson(exchange, 200, JsonSupport.savedSolutionJson(saved));
        }

        private void handleList(HttpExchange exchange) throws Exception {
            var params = parseQuery(exchange.getRequestURI().getRawQuery());
            var userId = params.get("userId");
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId query parameter is required");
            }
            var limit = parseLimit(params.get("limit"));
            var entries = repository.listRecent(userId, limit);
            writeJson(exchange, 200, JsonSupport.solveHistoryListJson(entries));
        }

        private void handleDetail(HttpExchange exchange, long solveId) throws Exception {
            var params = parseQuery(exchange.getRequestURI().getRawQuery());
            var userId = params.get("userId");
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("userId query parameter is required");
            }
            writeJson(exchange, 200, JsonSupport.solveHistoryDetailJson(repository.findDetail(userId, solveId)));
        }

        private static int parseLimit(String value) {
            if (value == null || value.isBlank()) {
                return 20;
            }
            return Math.max(1, Math.min(Integer.parseInt(value.trim()), 100));
        }

        private static SaveSolutionApiRequest readSolutionRequest(String body) {
            return new SaveSolutionApiRequest(
                    JsonSupport.readString(body, "userId"),
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
                    JsonSupport.readString(body, "pllStatus")
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

    private static final class StaticFileHandler implements HttpHandler {
        private final Path frontendDistDir;

        private StaticFileHandler(Path frontendDistDir) {
            this.frontendDistDir = frontendDistDir;
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

            if (frontendDistDir == null || !Files.isDirectory(frontendDistDir)) {
                writePlainText(exchange, 200, "Frontend build not available.");
                return;
            }

            var requestPath = exchange.getRequestURI().getPath();
            var relativePath = requestPath.equals("/") ? "index.html" : requestPath.substring(1);
            var target = frontendDistDir.resolve(relativePath).normalize();
            if (!target.startsWith(frontendDistDir) || Files.isDirectory(target) || !Files.exists(target)) {
                target = frontendDistDir.resolve("index.html");
            }

            var contentType = contentTypeFor(target);
            var bytes = Files.readAllBytes(target);
            var headers = exchange.getResponseHeaders();
            addCorsHeaders(headers);
            headers.set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        addCorsHeaders(headers);
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void writePlainText(HttpExchange exchange, int statusCode, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        addCorsHeaders(headers);
        headers.set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String contentTypeFor(Path file) {
        var name = file.getFileName().toString();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static java.util.Map<String, String> parseQuery(String rawQuery) {
        var result = new java.util.HashMap<String, String>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (var pair : rawQuery.split("&")) {
            var parts = pair.split("=", 2);
            var key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            var value = parts.length > 1 ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }
}
