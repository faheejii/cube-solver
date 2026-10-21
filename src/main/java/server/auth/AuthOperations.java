package server.auth;

import api.LoginRequest;
import api.RegisterRequest;
import database.AuthUser;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Authentication boundary consumed by the HTTP adapters. */
public interface AuthOperations {
    Duration SESSION_LIFETIME = Duration.ofDays(30);

    AuthSession register(RegisterRequest request);

    AuthSession login(LoginRequest request);

    AuthUser authenticate(String rawToken);

    void logout(String rawToken);

    record AuthSession(AuthUser user, String token, OffsetDateTime expiresAt) {
    }

    class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }

    class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }
}
