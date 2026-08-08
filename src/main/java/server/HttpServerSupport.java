package server;

import com.sun.net.httpserver.HttpExchange;
import database.AuthUser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Shared request parsing, authentication, configuration, and response policy for API routes. */
final class HttpServerSupport {
    private static final int MAX_JSON_BODY_BYTES = 64 * 1024;

    private HttpServerSupport() {
    }

    static boolean handleCors(HttpExchange exchange) throws IOException {
        return ApiResponses.handleCors(exchange);
    }

    static void addCorsHeaders(com.sun.net.httpserver.Headers headers) {
        ApiResponses.addCorsHeaders(headers);
    }

    static AuthUser requireAuthenticated(HttpExchange exchange, AuthService authService) throws Exception {
        var user = optionalAuthenticated(exchange, authService);
        if (user == null) {
            throw new AuthService.UnauthorizedException("Authentication required");
        }
        return user;
    }

    static AuthUser requireAdmin(HttpExchange exchange, AuthService authService) throws Exception {
        var user = requireAuthenticated(exchange, authService);
        if (!user.isAdmin()) {
            throw new AuthService.ForbiddenException("Administrator access required");
        }
        return user;
    }

    static AuthUser optionalAuthenticated(HttpExchange exchange, AuthService authService) throws Exception {
        return authService.authenticate(SessionCookie.read(exchange.getRequestHeaders()));
    }

    static String externalId(AuthUser user) {
        return user == null ? null : user.externalId();
    }

    static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        if (statusCode >= 400) {
            var message = JsonSupport.errorMessage(body);
            if (message != null) {
                ApiResponses.writeError(exchange, ApiResponses.codeFor(statusCode, message), message);
                return;
            }
        }
        ApiResponses.writeJson(exchange, statusCode, body);
    }

    static String readJsonBody(HttpExchange exchange) throws IOException {
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

    static boolean configuredSecureCookies() {
        return Boolean.parseBoolean(System.getProperty("server.cookie.secure", "true"));
    }

    static int configuredAuthRateLimit() {
        return configuredPositiveInteger("server.auth.requestsPerMinute", 20);
    }

    static int configuredHttpQueueSize() {
        return configuredPositiveInteger("server.http.queue", 128);
    }

    static java.util.Map<String, String> parseQuery(String rawQuery) {
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

    private static int configuredPositiveInteger(String property, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(System.getProperty(property, String.valueOf(fallback))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
