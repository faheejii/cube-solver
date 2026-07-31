package server;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTokenTest {
    @Test
    void generate_shouldCreateUniqueOpaque256BitTokens() {
        var generator = new SessionToken();
        var first = generator.generate();
        var second = generator.generate();

        assertNotEquals(first, second);
        assertEquals(32, Base64.getUrlDecoder().decode(first).length);
        assertEquals(32, Base64.getUrlDecoder().decode(second).length);
        assertTrue(SessionToken.isValid(first));
        assertFalse(SessionToken.isValid("malformed token"));
    }

    @Test
    void hash_shouldBeDeterministicWithoutRetainingRawToken() {
        var token = new SessionToken().generate();

        assertEquals(SessionToken.hash(token), SessionToken.hash(token));
        assertNotEquals(token, SessionToken.hash(token));
        assertEquals(32, Base64.getUrlDecoder().decode(SessionToken.hash(token)).length);
    }
}
