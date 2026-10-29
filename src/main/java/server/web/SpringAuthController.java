package server.web;

import api.LoginRequest;
import api.RegisterRequest;
import server.auth.AuthOperations;
import server.auth.AuthOperations.AuthSession;
import server.solve.SolveJobOperations;
import jakarta.servlet.http.HttpServletRequest;
import database.AuthUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springboot.config.ServerProperties;

/** MVC adapter for the Spring authentication service and cube_session cookie contract. */
@RestController
@RequestMapping("/api/auth")
final class SpringAuthController {
    private final SpringDatabaseHealth databaseHealth;
    private final AuthOperations authService;
    private final SolveJobOperations solveJobManager;
    private final RequestRateLimiter rateLimiter;
    private final ServerProperties serverProperties;

    SpringAuthController(
            SpringDatabaseHealth databaseHealth,
            AuthOperations authService,
            SolveJobOperations solveJobManager,
            RequestRateLimiter rateLimiter,
            ServerProperties serverProperties
    ) {
        this.databaseHealth = databaseHealth;
        this.authService = authService;
        this.solveJobManager = solveJobManager;
        this.rateLimiter = rateLimiter;
        this.serverProperties = serverProperties;
    }

    @PostMapping("/register")
    ResponseEntity<String> register(HttpServletRequest request, @RequestBody RegisterRequest body) throws Exception {
        ensureDatabase();
        checkRateLimit(request);
        var session = authService.register(body);
        return authenticatedResponse(201, session);
    }

    @PostMapping("/login")
    ResponseEntity<String> login(HttpServletRequest request, @RequestBody LoginRequest body) throws Exception {
        ensureDatabase();
        checkRateLimit(request);
        var session = authService.login(body);
        return authenticatedResponse(200, session);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) throws Exception {
        ensureDatabase();
        var token = SpringRequestSupport.sessionToken(request);
        AuthUser user = SpringRequestSupport.currentUser();
        if (user != null) {
            solveJobManager.cancelOwnedJobs(user.externalId());
        }
        authService.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookie.clear(serverProperties.getCookie().isSecure()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @GetMapping("/me")
    ResponseEntity<String> me() throws Exception {
        ensureDatabase();
        var user = SpringRequestSupport.requireUser();
        return SpringRequestSupport.json(200, JsonSupport.authUserJson(user));
    }

    private void ensureDatabase() {
        if (!databaseHealth.isConfigured()) {
            throw new SpringDatabaseUnavailableException();
        }
    }

    private void checkRateLimit(HttpServletRequest request) {
        var address = request.getRemoteAddr();
        if (!rateLimiter.tryAcquire(address == null ? "unknown" : address)) {
            throw new RateLimitExceededException();
        }
    }

    private ResponseEntity<String> authenticatedResponse(
            int status,
            AuthSession session
    ) {
        return ResponseEntity.status(status)
                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                .header(HttpHeaders.SET_COOKIE, SessionCookie.create(
                        session.token(),
                        AuthOperations.SESSION_LIFETIME,
                        serverProperties.getCookie().isSecure()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(JsonSupport.authUserJson(session.user()));
    }

    static final class RateLimitExceededException extends RuntimeException {
        RateLimitExceededException() {
            super("Too many authentication attempts");
        }
    }
}
