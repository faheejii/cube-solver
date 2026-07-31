package server;

import com.sun.net.httpserver.Headers;

import java.time.Duration;

final class SessionCookie {
    static final String NAME = "cube_session";

    private SessionCookie() {
    }

    static String read(Headers headers) {
        for (var header : headers.getOrDefault("Cookie", java.util.List.of())) {
            for (var cookie : header.split(";")) {
                var parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && NAME.equals(parts[0])) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    static String create(String token, Duration lifetime) {
        return NAME + "=" + token + "; Path=/; Max-Age=" + lifetime.toSeconds()
                + "; HttpOnly" + secureAttribute() + "; SameSite=Lax";
    }

    static String clear() {
        return NAME + "=; Path=/; Max-Age=0; HttpOnly" + secureAttribute() + "; SameSite=Lax";
    }

    private static String secureAttribute() {
        return Boolean.parseBoolean(System.getProperty("server.cookie.secure", "true"))
                ? "; Secure"
                : "";
    }
}
