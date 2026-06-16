package server;

import api.SolveApiRequest;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
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

    public CubeHttpServer(CfopSolveService solveService, Path frontendDistDir) {
        this.solveService = solveService;
        this.frontendDistDir = frontendDistDir;
    }

    public HttpServer create(int port) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", exchange -> writeJson(exchange, 200, "{\"status\":\"ok\"}"));
        server.createContext("/api/solve", new SolveHandler(solveService));
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
        headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
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
}
