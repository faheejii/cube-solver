package server;

import database.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shared request parsing, response, cookie, and authenticated-user policy for MVC adapters. */
final class SpringRequestSupport {
    static final int MAX_JSON_BODY_BYTES = 64 * 1024;

    private SpringRequestSupport() {
    }

    static ResponseEntity<String> json(int status, String body) {
        var requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(status)
                .header("X-Request-Id", requestId)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    static ResponseEntity<String> error(int status, String message) {
        var code = ApiResponses.codeFor(status, message);
        var requestId = UUID.randomUUID().toString();
        var body = "{\"error\":\"" + escape(message) + "\","
                + "\"code\":\"" + code.name() + "\","
                + "\"requestId\":\"" + requestId + "\"}";
        return ResponseEntity.status(status)
                .header("X-Request-Id", requestId)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    static String requireJson(String body) {
        if (body == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BODY_BYTES) {
            throw new IllegalArgumentException("Request body is too large");
        }
        return body;
    }

    static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return null;
        }
        return user;
    }

    static AuthUser requireUser() {
        var user = currentUser();
        if (user == null) {
            throw new AuthService.UnauthorizedException("Authentication required");
        }
        return user;
    }

    static AuthUser requireAdmin() {
        var user = requireUser();
        if (!user.isAdmin()) {
            throw new AuthService.ForbiddenException("Administrator access required");
        }
        return user;
    }

    static String sessionToken(HttpServletRequest request) {
        var cookies = request.getHeaders(HttpHeaders.COOKIE);
        while (cookies.hasMoreElements()) {
            for (var cookie : cookies.nextElement().split(";")) {
                var parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && SessionCookie.NAME.equals(parts[0])) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    static boolean isTrustedMutation(HttpServletRequest request) {
        var method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method))) {
            return true;
        }
        var origin = request.getHeader(HttpHeaders.ORIGIN);
        return origin == null || configuredCorsOrigin().equals(origin);
    }

    static void addCorsHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, configuredCorsOrigin());
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    static String configuredCorsOrigin() {
        return System.getProperty("server.cors.origin", "http://localhost:5173");
    }

    static String escape(String value) {
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
