package server;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCookieTest {
    @AfterEach
    void clearSecureProperty() {
        System.clearProperty("server.cookie.secure");
    }

    @Test
    void create_shouldSetRequiredSecurityAttributes() {
        var cookie = SessionCookie.create("opaque-token", Duration.ofDays(30));

        assertTrue(cookie.startsWith("cube_session=opaque-token;"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=2592000"));
    }

    @Test
    void read_shouldFindSessionAmongOtherCookies() {
        var headers = new Headers();
        headers.add("Cookie", "theme=dark; cube_session=opaque-token; locale=en");

        assertEquals("opaque-token", SessionCookie.read(headers));
        assertNull(SessionCookie.read(new Headers()));
    }

    @Test
    void clear_shouldExpireCookie() {
        var cookie = SessionCookie.clear();

        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
    }

    @Test
    void createAndClear_shouldAllowSecureAttributeToBeDisabledForLocalHttp() {
        System.setProperty("server.cookie.secure", "false");

        assertFalse(SessionCookie.create("opaque-token", Duration.ofMinutes(5)).contains("; Secure"));
        assertFalse(SessionCookie.clear().contains("; Secure"));
    }
}
