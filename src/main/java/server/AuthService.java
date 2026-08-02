package server;

import api.LoginRequest;
import api.RegisterRequest;
import database.AuthRepository;
import database.AuthUser;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

final class AuthService {
    static final Duration SESSION_LIFETIME = Duration.ofDays(30);
    private final AuthRepository repository;
    private final PasswordHasher passwordHasher;
    private final SessionToken sessionToken;
    private final Clock clock;

    AuthService(AuthRepository repository) {
        this(repository, new PasswordHasher(), new SessionToken(), Clock.systemUTC());
    }

    AuthService(AuthRepository repository, PasswordHasher passwordHasher, SessionToken sessionToken, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.sessionToken = sessionToken;
        this.clock = clock;
    }

    AuthenticatedSession register(RegisterRequest request) throws SQLException {
        var credential = passwordHasher.hash(request.password());
        var token = sessionToken.generate();
        var expiresAt = now().plus(SESSION_LIFETIME);
        AuthUser user;
        try {
            user = repository.createUserWithSession(
                    UUID.randomUUID().toString(),
                    request.email(),
                    request.displayName(),
                    credential.hash(),
                    credential.salt(),
                    credential.iterations(),
                    SessionToken.hash(token),
                    expiresAt
            );
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState())) {
                throw new AuthConflictException("An account with that email already exists");
            }
            throw exception;
        }
        return new AuthenticatedSession(user, token, expiresAt);
    }

    AuthenticatedSession login(LoginRequest request) throws SQLException {
        var credential = repository.findCredential(request.email());
        if (credential == null) {
            // Keep missing-account login work comparable to a normal PBKDF2 verification.
            passwordHasher.hash(request.password());
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!passwordHasher.verify(request.password(), credential)) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return createSession(credential.user());
    }

    AuthUser authenticate(String rawToken) throws SQLException {
        if (!SessionToken.isValid(rawToken)) {
            return null;
        }
        return repository.findSessionUser(SessionToken.hash(rawToken), now());
    }

    void logout(String rawToken) throws SQLException {
        if (SessionToken.isValid(rawToken)) {
            repository.deleteSession(SessionToken.hash(rawToken));
        }
    }

    private AuthenticatedSession createSession(AuthUser user) throws SQLException {
        var token = sessionToken.generate();
        var expiresAt = now().plus(SESSION_LIFETIME);
        repository.createSession(user.id(), SessionToken.hash(token), expiresAt);
        return new AuthenticatedSession(user, token, expiresAt);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
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
