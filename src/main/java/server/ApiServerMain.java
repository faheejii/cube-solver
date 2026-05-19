package server;

import com.sun.net.httpserver.HttpServer;
import solver.CfopSolveService;

import java.nio.file.Path;

public class ApiServerMain {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("server.port", "8080"));
        var frontendDistDir = Path.of(System.getProperty("frontend.dist", "frontend/dist")).toAbsolutePath().normalize();

        HttpServer server = new CubeHttpServer(new CfopSolveService(), frontendDistDir).create(port);
        System.out.println("Cube server listening on http://localhost:" + port);
        System.out.println("Frontend dist: " + frontendDistDir);
        server.start();
    }
}
