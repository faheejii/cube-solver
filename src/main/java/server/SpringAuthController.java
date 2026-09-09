package server;

import api.LoginRequest;
import api.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import database.AuthUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MVC adapter for the Spring authentication service and cube_session cookie contract. */
@RestController
@RequestMapping("/api/auth")
final class SpringAuthController {
    private final database.DatabaseManager databaseManager;
    private final SpringAuthService authService;
    private final SolveJobManager solveJobManager;
    private final RequestRateLimiter rateLimiter;

    SpringAuthController(
            database.DatabaseManager databaseManager,
            SpringAuthService authService,
            SolveJobManager solveJobManager,
            RequestRateLimiter rateLimiter
    ) {
        this.databaseManager = databaseManager;
        this.authService = authService;
        this.solveJobManager = solveJobManager;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    ResponseEntity<String> register(HttpServletRequest request, @RequestBody String body) throws Exception {
        ensureDatabase();
        checkRateLimit(request);
        var session = authService.register(new RegisterRequest(
                JsonSupport.readString(SpringRequestSupport.requireJson(body), "email"),
                JsonSupport.readString(body, "password"),
                JsonSupport.readString(body, "displayName")
        ));
        return authenticatedResponse(201, session);
    }

    @PostMapping("/login")
    ResponseEntity<String> login(HttpServletRequest request, @RequestBody String body) throws Exception {
        ensureDatabase();
        checkRateLimit(request);
        var json = SpringRequestSupport.requireJson(body);
        var session = authService.login(new LoginRequest(
                JsonSupport.readString(json, "email"),
                JsonSupport.readString(json, "password")
        ));
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
                .header(HttpHeaders.SET_COOKIE, SessionCookie.clear(HttpServerSupport.configuredSecureCookies()))
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
        if (!databaseManager.isConfigured()) {
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
            SpringAuthContracts.AuthenticatedSession session
    ) {
        return ResponseEntity.status(status)
                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                .header(HttpHeaders.SET_COOKIE, SessionCookie.create(
                        session.token(),
                        SpringAuthContracts.SESSION_LIFETIME,
                        HttpServerSupport.configuredSecureCookies()))
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
