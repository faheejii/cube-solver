package server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shared HTTP response and request-policy helpers used by every API route. */
final class ApiResponses {
    private ApiResponses() {
    }

    static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        var requestId = UUID.randomUUID().toString();
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        addCorsHeaders(headers);
        headers.set("X-Request-Id", requestId);
        headers.set("Cache-Control", "private, no-store");
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static void writeError(HttpExchange exchange, ApiErrorCode code, String message) throws IOException {
        var requestId = UUID.randomUUID().toString();
        var body = "{\"error\":\"" + escape(message) + "\","
                + "\"code\":\"" + code.name() + "\","
                + "\"requestId\":\"" + requestId + "\"}";
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        addCorsHeaders(headers);
        headers.set("X-Request-Id", requestId);
        headers.set("Cache-Control", "private, no-store");
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static boolean handleCors(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        var origin = exchange.getRequestHeaders().getFirst("Origin");
        if (isMutation(exchange.getRequestMethod())
                && origin != null
                && !configuredCorsOrigin().equals(origin)) {
            writeError(exchange, ApiErrorCode.FORBIDDEN, "Untrusted request origin");
            return true;
        }
        return false;
    }

    static void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", configuredCorsOrigin());
        headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("Access-Control-Allow-Credentials", "true");
    }

    static ApiErrorCode codeFor(int status, String message) {
        if (status == 400 && "Method not allowed".equals(message)) {
            return ApiErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status == 400 && ("Solve job not found".equals(message)
                || "Solve not found".equals(message))) {
            return ApiErrorCode.NOT_FOUND;
        }
        for (var code : ApiErrorCode.values()) {
            if (code.status() == status) {
                return code;
            }
        }
        return status == 400 ? ApiErrorCode.BAD_REQUEST : ApiErrorCode.INTERNAL_ERROR;
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

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}
