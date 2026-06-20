package server;

import com.sun.net.httpserver.HttpServer;
import database.DatabaseManager;
import solver.CfopSolveService;

import java.nio.file.Path;

public class ApiServerMain {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("server.port", "8080"));
        var frontendDistDir = Path.of(System.getProperty("frontend.dist", "frontend/dist")).toAbsolutePath().normalize();
        var databaseManager = DatabaseManager.fromEnvironment();
        databaseManager.initialize();

        HttpServer server = new CubeHttpServer(new CfopSolveService(), frontendDistDir, databaseManager).create(port);
        System.out.println("Cube server listening on http://localhost:" + port);
        System.out.println("Frontend dist: " + frontendDistDir);
        System.out.println("Database: " + (databaseManager.isConfigured() ? "configured" : "disabled (set DATABASE_URL)"));
        server.start();
    }
}
