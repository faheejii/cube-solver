package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the built frontend and falls back to its entry point for client routes. */
final class StaticFileHandler implements HttpHandler {
    private final Path frontendDistDir;

    StaticFileHandler(Path frontendDistDir) {
        this.frontendDistDir = frontendDistDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (ApiResponses.handleCors(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            ApiResponses.writeError(exchange, ApiErrorCode.METHOD_NOT_ALLOWED, "Method not allowed");
            return;
        }

        if (frontendDistDir == null || !Files.isDirectory(frontendDistDir)) {
            writePlainText(exchange, 200, "Frontend build not available.");
            return;
        }

        var requestPath = exchange.getRequestURI().getPath();
        var relativePath = requestPath.equals("/") ? "index.html" : requestPath.substring(1);
        var target = frontendDistDir.resolve(relativePath).normalize();
        target = resolveGeneratedWorkerAlias(relativePath, target);
        if (!target.startsWith(frontendDistDir) || Files.isDirectory(target) || !Files.exists(target)) {
            if (hasFileExtension(relativePath)) {
                writePlainText(exchange, 404, "Not found");
                return;
            }
            target = frontendDistDir.resolve("index.html");
        }

        var bytes = Files.readAllBytes(target);
        var headers = exchange.getResponseHeaders();
        ApiResponses.addCorsHeaders(headers);
        headers.set("Content-Type", contentTypeFor(target));
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void writePlainText(HttpExchange exchange, int statusCode, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        ApiResponses.addCorsHeaders(headers);
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

    private static boolean hasFileExtension(String path) {
        var lastSegment = path.substring(path.lastIndexOf('/') + 1);
        var extensionIndex = lastSegment.lastIndexOf('.');
        return extensionIndex > 0 && extensionIndex < lastSegment.length() - 1;
    }

    private Path resolveGeneratedWorkerAlias(String relativePath, Path target) throws IOException {
        if (Files.exists(target) || !relativePath.equals("assets/search-worker-entry.js")) {
            return target;
        }
        try (var files = Files.list(frontendDistDir.resolve("assets"))) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith("search-worker-entry-"))
                    .filter(path -> path.getFileName().toString().endsWith(".js"))
                    .findFirst()
                    .orElse(target);
        }
    }
}
