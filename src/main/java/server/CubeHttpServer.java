package server;

import com.sun.net.httpserver.HttpServer;
import database.AuthRepository;
import database.DatabaseManager;
import solver.CfopSolveService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

import static server.HttpServerSupport.configuredHttpQueueSize;
import static server.HttpServerSupport.configuredSecureCookies;

public class CubeHttpServer implements AutoCloseable {
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
        server.createContext("/api/metrics", healthMetricsHandler);
        server.createContext("/api/auth", new AuthRouteHandler(
                databaseManager, authService, solveJobManager, secureCookies));
        server.createContext("/api/solve", new SolveRouteHandler(solveJobManager));
        server.createContext("/api/solve-jobs", new SolveJobRouteHandler(solveJobManager, authService));
        server.createContext("/api/solves", new SolveHistoryRouteHandler(
                databaseManager, solveJobManager, authService));
        server.createContext("/api/stats", new StatisticsRouteHandler(databaseManager, authService));
        server.createContext("/api/algorithms", new AlgorithmCatalogRouteHandler(databaseManager, authService));
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
}
