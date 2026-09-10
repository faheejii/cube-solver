package server;

import database.AuthUser;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Authentication contracts owned by the Spring MVC/Security runtime. */
final class SpringAuthContracts {
    static final Duration SESSION_LIFETIME = Duration.ofDays(30);

    private SpringAuthContracts() {
    }

    record AuthenticatedSession(AuthUser user, String token, OffsetDateTime expiresAt) {
    }

    static final class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String message) {
            super(message);
        }
    }

    static final class AuthConflictException extends RuntimeException {
        AuthConflictException(String message) {
            super(message);
        }
    }

    static final class ForbiddenException extends RuntimeException {
        ForbiddenException(String message) {
            super(message);
        }
    }
}
