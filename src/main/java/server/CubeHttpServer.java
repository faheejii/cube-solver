package server;

import api.CreateSolveAttemptRequest;
import api.CreateSolveJobRequest;
import api.LoginRequest;
import api.RegisterRequest;
import api.SaveSolutionApiRequest;
import api.SolveApiRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import database.DatabaseManager;
import database.AuthRepository;
import database.AuthUser;
import database.CreateSolveAttemptCommand;
import database.SaveSolutionCommand;
import database.SolveHistoryRepository;
import solver.CfopSolveService;
import solver.SolveDeadlineExceededException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class CubeHttpServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CubeHttpServer.class);
    private static final int MAX_JSON_BODY_BYTES = 64 * 1024;
    private final Path frontendDistDir;
    private final DatabaseManager databaseManager;
    private final SolveJobManager solveJobManager;
    private final AuthService authService;
    private final OperationalMetrics operationalMetrics;
    private final boolean secureCookies;
    private java.util.concurrent.ExecutorService httpExecutor;

    public CubeHttpServer(CfopSolveService solveService, Path frontendDistDir, DatabaseManager databaseManager) {
        this(solveService, frontendDistDir, databaseManager, configuredSecureCookies());
    }

    CubeHttpServer(
            CfopSolveService solveService,
            Path frontendDistDir,
            DatabaseManager databaseManager,
            boolean secureCookies
    ) {
        this.frontendDistDir = frontendDistDir;
        this.databaseManager = databaseManager;
        this.secureCookies = secureCookies;
        this.operationalMetrics = new OperationalMetrics();
        this.solveJobManager = new SolveJobManager(solveService, databaseManager, operationalMetrics);
        this.authService = new AuthService(new AuthRepository(databaseManager));
    }

    public HttpServer create(int port) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        var healthMetricsHandler = new HealthMetricsHandler(databaseManager, operationalMetrics);
        server.createContext("/api/health/live", healthMetricsHandler);
        server.createContext("/api/health/ready", healthMetricsHandler);
        server.createContext("/api/health", healthMetricsHandler);
        server.createContext("/api/metrics", healthMetricsHandler);
        server.createContext("/api/auth", new AuthHandler(databaseManager, authService, solveJobManager, secureCookies));
        server.createContext("/api/solve", new SolveHandler(solveJobManager));
        server.createContext("/api/solve-jobs", new SolveJobHandler(solveJobManager, authService));
        server.createContext("/api/solves", new SolveHistoryHandler(databaseManager, solveJobManager, authService));
        server.createContext("/api/stats", new StatisticsHandler(databaseManager, authService));
        server.createContext("/", new StaticFileHandler(frontendDistDir));
        int httpWorkers = Math.max(4, Runtime.getRuntime().availableProcessors());
        httpExecutor = new java.util.concurrent.ThreadPoolExecutor(
                httpWorkers,
                httpWorkers,
                0L,
                java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(configuredHttpQueueSize()),
                runnable -> {
                    var thread = new Thread(runnable, "cube-http-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
        server.setExecutor(httpExecutor);
        return server;
    }

    @Override
    public void close() {
        solveJobManager.close();
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
        }
    }

    private static final class AuthHandler implements HttpHandler {
        private final DatabaseManager databaseManager;
        private final AuthService authService;
        private final SolveJobManager solveJobManager;
        private final boolean secureCookies;
        private final RequestRateLimiter rateLimiter = new RequestRateLimiter(
                configuredAuthRateLimit(),
                java.time.Duration.ofMinutes(1)
        );

        private AuthHandler(
                DatabaseManager databaseManager,
                AuthService authService,
                SolveJobManager solveJobManager,
                boolean secureCookies
        ) {
            this.databaseManager = databaseManager;
            this.authService = authService;
            this.solveJobManager = solveJobManager;
            this.secureCookies = secureCookies;
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
                var parts = java.util.Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                        .filter(part -> !part.isBlank())
                        .toList();
                if (parts.size() != 3) {
                    writeJson(exchange, 404, JsonSupport.errorJson("Not found"));
                    return;
                }
                if (("register".equals(parts.get(2)) || "login".equals(parts.get(2)))
                        && !rateLimiter.tryAcquire(exchange.getRemoteAddress().getAddress().getHostAddress())) {
                    writeJson(exchange, 429, JsonSupport.errorJson("Too many authentication attempts"));
                    return;
                }
                switch (parts.get(2)) {
                    case "register" -> register(exchange);
                    case "login" -> login(exchange);
                    case "logout" -> logout(exchange);
                    case "me" -> me(exchange);
                    default -> writeJson(exchange, 404, JsonSupport.errorJson("Not found"));
                }
            } catch (AuthService.UnauthorizedException exception) {
                writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
            } catch (AuthService.AuthConflictException exception) {
                writeJson(exchange, 409, JsonSupport.errorJson(exception.getMessage()));
            } catch (ApiMethodNotAllowedException exception) {
                writeJson(exchange, 405, JsonSupport.errorJson(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
            } catch (Exception exception) {
                LOGGER.error("Authentication request failed", exception);
                writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
            }
        }

        private void register(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            var body = readJsonBody(exchange);
            var session = authService.register(new RegisterRequest(
                    JsonSupport.readString(body, "email"),
                    JsonSupport.readString(body, "password"),
                    JsonSupport.readString(body, "displayName")
            ));
            writeAuthenticated(exchange, 201, session);
        }

        private void login(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            var body = readJsonBody(exchange);
            var session = authService.login(new LoginRequest(
                    JsonSupport.readString(body, "email"),
                    JsonSupport.readString(body, "password")
            ));
            writeAuthenticated(exchange, 200, session);
        }

        private void logout(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "POST");
            var token = SessionCookie.read(exchange.getRequestHeaders());
            var user = authService.authenticate(token);
            if (user != null) {
                solveJobManager.cancelOwnedJobs(user.externalId());
            }
            authService.logout(token);
            exchange.getResponseHeaders().add("Set-Cookie", SessionCookie.clear(secureCookies));
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            addCorsHeaders(exchange.getResponseHeaders());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        private void me(HttpExchange exchange) throws Exception {
            requireMethod(exchange, "GET");
            var user = requireAuthenticated(exchange, authService);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            writeJson(exchange, 200, JsonSupport.authUserJson(user));
        }

        private void writeAuthenticated(
                HttpExchange exchange,
                int status,
                AuthService.AuthenticatedSession session
        ) throws IOException {
            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    SessionCookie.create(session.token(), AuthService.SESSION_LIFETIME, secureCookies)
            );
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            writeJson(exchange, status, JsonSupport.authUserJson(session.user()));
        }

        private static void requireMethod(HttpExchange exchange, String expected) {
            if (!expected.equalsIgnoreCase(exchange.getRequestMethod())) {
                throw new ApiMethodNotAllowedException();
            }
        }
    }

    private static final class SolveJobHandler implements HttpHandler {
        private final SolveJobManager jobManager;
        private final AuthService authService;

        private SolveJobHandler(SolveJobManager jobManager, AuthService authService) {
            this.jobManager = jobManager;
            this.authService = authService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) {
                return;
            }

            try {
                var pathParts = java.util.Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                        .filter(part -> !part.isBlank())
                        .toList();
                if (pathParts.size() == 2 && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleCreate(exchange);
                    return;
                }
                if (pathParts.size() == 3 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    var user = optionalAuthenticated(exchange, authService);
                    writeJson(exchange, 200, JsonSupport.solveJobJson(jobManager.find(
                            pathParts.get(2), externalId(user))));
                    return;
                }
                if (pathParts.size() == 3 && "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    var user = optionalAuthenticated(exchange, authService);
                    writeJson(exchange, 200, JsonSupport.solveJobJson(jobManager.cancel(
                            pathParts.get(2), externalId(user))));
                    return;
                }
                writeJson(exchange, 405, JsonSupport.errorJson("Method not allowed"));
            } catch (SolveJobManager.CapacityException exception) {
                writeJson(exchange, 429, JsonSupport.errorJson(exception.getMessage()));
            } catch (SolveJobManager.AuthenticationRequiredException exception) {
                writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
            } catch (SolveJobManager.ForbiddenException exception) {
                writeJson(exchange, 403, JsonSupport.errorJson(exception.getMessage()));
            } catch (AuthService.UnauthorizedException exception) {
                writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
            } catch (SolveDeadlineExceededException exception) {
                writeJson(exchange, 504, JsonSupport.errorJson(exception.getMessage()));
            } catch (Exception exception) {
                LOGGER.error("Synchronous solve request failed", exception);
                writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
            }
        }

        private void handleCreate(HttpExchange exchange) throws Exception {
            var body = readJsonBody(exchange);
            var request = new CreateSolveJobRequest(
                    JsonSupport.readString(body, "scramble"),
                    JsonSupport.readString(body, "crossFace"),
                    JsonSupport.readString(body, "f2lMode"),
                    JsonSupport.readLong(body, "solveId"),
                    JsonSupport.readBoolean(body, "saveOnComplete")
            );
            var user = optionalAuthenticated(exchange, authService);
            if (request.saveOnComplete() && user == null) {
                throw new AuthService.UnauthorizedException("Authentication required");
            }
            var job = jobManager.submit(
                    request.solveRequest(),
                    externalId(user),
                    request.solveId(),
                    request.saveOnComplete()
            );
            writeJson(exchange, 202, JsonSupport.solveJobJson(job));
        }
    }

    private static final class SolveHandler implements HttpHandler {
        private final SolveJobManager jobManager;

        private SolveHandler(SolveJobManager jobManager) {
            this.jobManager = jobManager;
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
                var body = readJsonBody(exchange);
                var request = new SolveApiRequest(
                        JsonSupport.readString(body, "scramble"),
                        JsonSupport.readString(body, "crossFace"),
                        JsonSupport.readString(body, "f2lMode")
                );
                var job = jobManager.submit(request, null, null, false);
                while (true) {
                    var snapshot = jobManager.find(job.id());
                    if ("completed".equals(snapshot.status()) && snapshot.result() != null) {
                        writeJson(exchange, 200, JsonSupport.solveResultJson(snapshot.result()));
                        return;
                    }
                    if ("timed_out".equals(snapshot.status())) {
                        writeJson(exchange, 504, JsonSupport.errorJson(snapshot.error()));
                        return;
                    }
                    if ("failed".equals(snapshot.status())) {
                        writeJson(exchange, 500, JsonSupport.errorJson(snapshot.error()));
                        return;
                    }
                    if ("cancelled".equals(snapshot.status())) {
                        writeJson(exchange, 409, JsonSupport.errorJson(snapshot.error()));
                        return;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        jobManager.cancel(job.id());
                        throw new IOException("Solve request interrupted", exception);
                    }
                }
            } catch (IllegalArgumentException exception) {
                writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
            } catch (SolveJobManager.CapacityException exception) {
                writeJson(exchange, 429, JsonSupport.errorJson(exception.getMessage()));
            } catch (SolveDeadlineExceededException exception) {
                writeJson(exchange, 504, JsonSupport.errorJson(exception.getMessage()));
            } catch (Exception exception) {
                LOGGER.error("Solve request failed", exception);
                writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
            }
        }
    }

    private static final class SolveHistoryHandler implements HttpHandler {
        private final DatabaseManager databaseManager;
        private final SolveHistoryRepository repository;
        private final SolveJobManager solveJobManager;
        private final AuthService authService;

        private SolveHistoryHandler(
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
                    DatabaseManager.SOLVER_VERSION
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

    private static final class StatisticsHandler implements HttpHandler {
        private final DatabaseManager databaseManager;
        private final SolveHistoryRepository repository;
        private final AuthService authService;

        private StatisticsHandler(DatabaseManager databaseManager, AuthService authService) {
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

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        return ApiResponses.handleCors(exchange);
    }

    private static void addCorsHeaders(com.sun.net.httpserver.Headers headers) {
        ApiResponses.addCorsHeaders(headers);
    }

    private static AuthUser requireAuthenticated(HttpExchange exchange, AuthService authService) throws Exception {
        var user = optionalAuthenticated(exchange, authService);
        if (user == null) {
            throw new AuthService.UnauthorizedException("Authentication required");
        }
        return user;
    }

    private static AuthUser optionalAuthenticated(HttpExchange exchange, AuthService authService) throws Exception {
        return authService.authenticate(SessionCookie.read(exchange.getRequestHeaders()));
    }

    private static String externalId(AuthUser user) {
        return user == null ? null : user.externalId();
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        if (statusCode >= 400) {
            var message = JsonSupport.errorMessage(body);
            if (message != null) {
                ApiResponses.writeError(exchange, ApiResponses.codeFor(statusCode, message), message);
                return;
            }
        }
        ApiResponses.writeJson(exchange, statusCode, body);
    }

    private static String readJsonBody(HttpExchange exchange) throws IOException {
        var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
            throw new IllegalArgumentException("Content-Type must be application/json");
        }
        var contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > MAX_JSON_BODY_BYTES) {
                    throw new IllegalArgumentException("Request body is too large");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid Content-Length");
            }
        }
        try (InputStream input = exchange.getRequestBody()) {
            var bytes = input.readNBytes(MAX_JSON_BODY_BYTES + 1);
            if (bytes.length > MAX_JSON_BODY_BYTES) {
                throw new IllegalArgumentException("Request body is too large");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static boolean isMutation(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private static String configuredCorsOrigin() {
        return System.getProperty("server.cors.origin", "http://localhost:5173");
    }

    private static boolean configuredSecureCookies() {
        return Boolean.parseBoolean(System.getProperty("server.cookie.secure", "true"));
    }

    private static int configuredAuthRateLimit() {
        return configuredPositiveInteger("server.auth.requestsPerMinute", 20);
    }

    private static int configuredHttpQueueSize() {
        return configuredPositiveInteger("server.http.queue", 128);
    }

    private static int configuredPositiveInteger(String property, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(System.getProperty(property, String.valueOf(fallback))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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
