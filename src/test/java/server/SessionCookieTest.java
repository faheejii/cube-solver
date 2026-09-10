package server;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieTest {
    @Test
    void create_shouldSetRequiredSecurityAttributes() {
        var cookie = SessionCookie.create("opaque-token", Duration.ofDays(30), true);

        assertTrue(cookie.startsWith("cube_session=opaque-token;"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=2592000"));
    }

    @Test
    void read_shouldFindSessionAmongOtherCookies() {
        var headers = List.of("theme=dark; cube_session=opaque-token; locale=en");

        assertEquals("opaque-token", SessionCookie.read(headers));
        assertNull(SessionCookie.read(List.of()));
    }

    @Test
    void clear_shouldExpireCookie() {
        var cookie = SessionCookie.clear(true);

        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
    }

    @Test
    void createAndClear_shouldAllowSecureAttributeToBeDisabledForLocalHttp() {
        assertFalse(SessionCookie.create("opaque-token", Duration.ofMinutes(5), false).contains("; Secure"));
        assertFalse(SessionCookie.clear(false).contains("; Secure"));
    }
}
