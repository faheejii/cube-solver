package server;

import api.LoginRequest;
import api.RegisterRequest;
import database.AuthUser;
import database.StoredCredential;
import database.persistence.entity.AuthSessionEntity;
import database.persistence.entity.UserEntity;
import database.persistence.repository.AuthSessionJpaRepository;
import database.persistence.repository.UserJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Spring/JPA authentication path that preserves the legacy cookie and password contracts. */
@Service
final class SpringAuthService {
    static final Duration SESSION_LIFETIME = AuthService.SESSION_LIFETIME;

    private final UserJpaRepository users;
    private final AuthSessionJpaRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SessionToken sessionToken;
    private final Clock clock;
    private final String adminEmail;

    SpringAuthService(UserJpaRepository users, AuthSessionJpaRepository sessions) {
        this(users, sessions, new PasswordHasher(), new SessionToken(), Clock.systemUTC(),
                database.DatabaseConfig.adminEmailFromEnvironment());
    }

    SpringAuthService(
            UserJpaRepository users,
            AuthSessionJpaRepository sessions,
            PasswordHasher passwordHasher,
            SessionToken sessionToken,
            Clock clock,
            String adminEmail
    ) {
        this.users = users;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.sessionToken = sessionToken;
        this.clock = clock;
        this.adminEmail = adminEmail;
    }

    @Transactional
    AuthService.AuthenticatedSession register(RegisterRequest request) {
        var credential = passwordHasher.hash(request.password());
        var token = sessionToken.generate();
        var expiresAt = now().plus(SESSION_LIFETIME);
        var user = new UserEntity(UUID.randomUUID().toString(), request.email(), request.displayName());
        user.setPasswordHash(credential.hash());
        user.setPasswordSalt(credential.salt());
        user.setPasswordIterations(credential.iterations());
        user.setRole(isAdminEmail(request.email()) ? "admin" : "user");

        try {
            user = users.saveAndFlush(user);
            sessions.save(new AuthSessionEntity(user, SessionToken.hash(token), expiresAt));
        } catch (DataIntegrityViolationException exception) {
            throw new AuthService.AuthConflictException("An account with that email already exists");
        }
        return new AuthService.AuthenticatedSession(toAuthUser(user), token, expiresAt);
    }

    @Transactional
    AuthService.AuthenticatedSession login(LoginRequest request) {
        var user = users.findCredentialByEmail(request.email()).orElse(null);
        if (user == null) {
            // Keep missing-account login work comparable to a normal PBKDF2 verification.
            passwordHasher.hash(request.password());
            throw new AuthService.UnauthorizedException("Invalid email or password");
        }
        var credential = new StoredCredential(
                toAuthUser(user),
                user.getPasswordHash(),
                user.getPasswordSalt(),
                user.getPasswordIterations() == null ? 0 : user.getPasswordIterations()
        );
        if (!passwordHasher.verify(request.password(), credential)) {
            throw new AuthService.UnauthorizedException("Invalid email or password");
        }
        var token = sessionToken.generate();
        var expiresAt = now().plus(SESSION_LIFETIME);
        sessions.save(new AuthSessionEntity(user, SessionToken.hash(token), expiresAt));
        return new AuthService.AuthenticatedSession(toAuthUser(user), token, expiresAt);
    }

    @Transactional
    AuthUser authenticate(String rawToken) {
        if (!SessionToken.isValid(rawToken)) {
            return null;
        }
        var session = sessions.findByTokenHashAndExpiresAtAfter(SessionToken.hash(rawToken), now()).orElse(null);
        if (session == null) {
            return null;
        }
        session.setLastSeenAt(now());
        return toAuthUser(session.getUser());
    }

    @Transactional
    void logout(String rawToken) {
        if (SessionToken.isValid(rawToken)) {
            sessions.deleteByTokenHash(SessionToken.hash(rawToken));
        }
    }

    private AuthUser toAuthUser(UserEntity user) {
        return new AuthUser(user.getId(), user.getExternalId(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    private boolean isAdminEmail(String email) {
        return adminEmail != null && email != null && adminEmail.equalsIgnoreCase(email.trim());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
