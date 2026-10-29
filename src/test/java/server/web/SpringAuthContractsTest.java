package server.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import server.auth.AuthOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused coverage for the Spring-owned authentication contract and error mapping. */
class SpringAuthContractsTest {
    private final SpringExceptionHandler exceptionHandler = new SpringExceptionHandler();

    @Test
    void preservesTheExistingSessionLifetime() {
        assertEquals(Duration.ofDays(30), AuthOperations.SESSION_LIFETIME);
    }

    @Test
    void mapsAuthenticationErrorsToTheExistingJsonContract() {
        assertError(exceptionHandler.unauthorized(
                new AuthOperations.UnauthorizedException("Authentication required")),
                401, "Authentication required");
        assertError(exceptionHandler.conflict(
                new AuthOperations.ConflictException("An account with that email already exists")),
                409, "An account with that email already exists");
        assertError(exceptionHandler.forbidden(
                new AuthOperations.ForbiddenException("Administrator access required")),
                403, "Administrator access required");
    }

    private static void assertError(ResponseEntity<String> response, int status, String message) {
        assertEquals(status, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"error\":\"" + message + "\""));
        assertTrue(response.getBody().contains("\"requestId\":"));
    }
}
