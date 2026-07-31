package test;

import api.LoginRequest;
import api.RegisterRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRequestTest {
    @Test
    void register_shouldNormalizeIdentityFields() {
        var request = new RegisterRequest(
                " User@Example.COM ",
                "long-enough-password",
                "  Cube User  "
        );

        assertEquals("user@example.com", request.email());
        assertEquals("Cube User", request.displayName());
    }

    @Test
    void register_shouldRejectWeakPasswordAndInvalidEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> new RegisterRequest("user@example.com", "short", null));
        assertThrows(IllegalArgumentException.class,
                () -> new RegisterRequest("not-an-email", "long-enough-password", null));
    }

    @Test
    void login_shouldNormalizeEmailAndRequireCredentials() {
        assertEquals(
                "user@example.com",
                new LoginRequest(" USER@EXAMPLE.COM ", "password").email()
        );
        assertThrows(IllegalArgumentException.class, () -> new LoginRequest("", "password"));
    }
}
