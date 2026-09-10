package server;

import java.time.Duration;

final class SessionCookie {
    static final String NAME = "cube_session";

    private SessionCookie() {
    }

    static String read(Iterable<String> cookieHeaders) {
        for (var header : cookieHeaders) {
            for (var cookie : header.split(";")) {
                var parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && NAME.equals(parts[0])) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    static String create(String token, Duration lifetime, boolean secure) {
        return NAME + "=" + token + "; Path=/; Max-Age=" + lifetime.toSeconds()
                + "; HttpOnly" + secureAttribute(secure) + "; SameSite=Lax";
    }

    static String clear(boolean secure) {
        return NAME + "=; Path=/; Max-Age=0; HttpOnly" + secureAttribute(secure) + "; SameSite=Lax";
    }

    private static String secureAttribute(boolean secure) {
        return secure ? "; Secure" : "";
    }
}
