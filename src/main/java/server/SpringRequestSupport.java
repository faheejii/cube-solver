package server;

import database.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
        var code = errorCode(status, message);
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
            throw new SpringAuthContracts.UnauthorizedException("Authentication required");
        }
        return user;
    }

    static AuthUser requireAdmin() {
        var user = requireUser();
        if (!user.isAdmin()) {
            throw new SpringAuthContracts.ForbiddenException("Administrator access required");
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

    static boolean isTrustedMutation(HttpServletRequest request, String corsOrigin) {
        var method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method))) {
            return true;
        }
        var origin = request.getHeader(HttpHeaders.ORIGIN);
        return origin == null || corsOrigin.equals(origin);
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

    private static ApiErrorCode errorCode(int status, String message) {
        if (status == 405) {
            return ApiErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status == 404 || "Solve not found".equals(message) || "Solve job not found".equals(message)) {
            return ApiErrorCode.NOT_FOUND;
        }
        if (status == 401) return ApiErrorCode.UNAUTHORIZED;
        if (status == 403) return ApiErrorCode.FORBIDDEN;
        if (status == 409) return ApiErrorCode.CONFLICT;
        if (status == 429) return ApiErrorCode.RATE_LIMITED;
        if (status == 504) return ApiErrorCode.TIMEOUT;
        if (status == 503) return ApiErrorCode.SERVICE_UNAVAILABLE;
        if (status >= 500) return ApiErrorCode.INTERNAL_ERROR;
        return ApiErrorCode.BAD_REQUEST;
    }
}
