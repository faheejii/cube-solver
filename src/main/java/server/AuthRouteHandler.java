package server;

import api.LoginRequest;
import api.RegisterRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import database.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import static server.HttpServerSupport.addCorsHeaders;
import static server.HttpServerSupport.configuredAuthRateLimit;
import static server.HttpServerSupport.handleCors;
import static server.HttpServerSupport.readJsonBody;
import static server.HttpServerSupport.requireAuthenticated;
import static server.HttpServerSupport.writeJson;

final class AuthRouteHandler implements HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthRouteHandler.class);
    private final DatabaseManager databaseManager;
    private final AuthService authService;
    private final SolveJobManager solveJobManager;
    private final boolean secureCookies;
    private final RequestRateLimiter rateLimiter = new RequestRateLimiter(
            configuredAuthRateLimit(),
            java.time.Duration.ofMinutes(1)
    );

    AuthRouteHandler(
            DatabaseManager databaseManager,
            AuthService authService,
            SolveJobManager solveJobManager,
            boolean secureCookies
    ) {
        this.databaseManager = databaseManager;
        this.authService = authService;
        this.solveJobManager = solveJobManager;
        this.secureCookies = secureCookies;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }
        if (!databaseManager.isConfigured()) {
            writeJson(exchange, 503, JsonSupport.errorJson("Database is not configured"));
            return;
        }

        try {
            var parts = java.util.Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                    .filter(part -> !part.isBlank())
                    .toList();
            if (parts.size() != 3) {
                writeJson(exchange, 404, JsonSupport.errorJson("Not found"));
                return;
            }
            if (("register".equals(parts.get(2)) || "login".equals(parts.get(2)))
                    && !rateLimiter.tryAcquire(exchange.getRemoteAddress().getAddress().getHostAddress())) {
                writeJson(exchange, 429, JsonSupport.errorJson("Too many authentication attempts"));
                return;
            }
            switch (parts.get(2)) {
                case "register" -> register(exchange);
                case "login" -> login(exchange);
                case "logout" -> logout(exchange);
                case "me" -> me(exchange);
                default -> writeJson(exchange, 404, JsonSupport.errorJson("Not found"));
            }
        } catch (AuthService.UnauthorizedException exception) {
            writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
        } catch (AuthService.AuthConflictException exception) {
            writeJson(exchange, 409, JsonSupport.errorJson(exception.getMessage()));
        } catch (ApiMethodNotAllowedException exception) {
            writeJson(exchange, 405, JsonSupport.errorJson(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
        } catch (Exception exception) {
            LOGGER.error("Authentication request failed", exception);
            writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
        }
    }

    private void register(HttpExchange exchange) throws Exception {
        requireMethod(exchange, "POST");
        var body = readJsonBody(exchange);
        var session = authService.register(new RegisterRequest(
                JsonSupport.readString(body, "email"),
                JsonSupport.readString(body, "password"),
                JsonSupport.readString(body, "displayName")
        ));
        writeAuthenticated(exchange, 201, session);
    }

    private void login(HttpExchange exchange) throws Exception {
        requireMethod(exchange, "POST");
        var body = readJsonBody(exchange);
        var session = authService.login(new LoginRequest(
                JsonSupport.readString(body, "email"),
                JsonSupport.readString(body, "password")
        ));
        writeAuthenticated(exchange, 200, session);
    }

    private void logout(HttpExchange exchange) throws Exception {
        requireMethod(exchange, "POST");
        var token = SessionCookie.read(exchange.getRequestHeaders());
        var user = authService.authenticate(token);
        if (user != null) {
            solveJobManager.cancelOwnedJobs(user.externalId());
        }
        authService.logout(token);
        exchange.getResponseHeaders().add("Set-Cookie", SessionCookie.clear(secureCookies));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        addCorsHeaders(exchange.getResponseHeaders());
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void me(HttpExchange exchange) throws Exception {
        requireMethod(exchange, "GET");
        var user = requireAuthenticated(exchange, authService);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        writeJson(exchange, 200, JsonSupport.authUserJson(user));
    }

    private void writeAuthenticated(
            HttpExchange exchange,
            int status,
            AuthService.AuthenticatedSession session
    ) throws IOException {
        exchange.getResponseHeaders().add(
                "Set-Cookie",
                SessionCookie.create(session.token(), AuthService.SESSION_LIFETIME, secureCookies)
        );
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        writeJson(exchange, status, JsonSupport.authUserJson(session.user()));
    }

    private static void requireMethod(HttpExchange exchange, String expected) {
        if (!expected.equalsIgnoreCase(exchange.getRequestMethod())) {
            throw new ApiMethodNotAllowedException();
        }
    }
}
