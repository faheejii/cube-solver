package server;

import api.CreateSolveJobRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solver.SolveDeadlineExceededException;

import java.io.IOException;

import static server.HttpServerSupport.externalId;
import static server.HttpServerSupport.handleCors;
import static server.HttpServerSupport.optionalAuthenticated;
import static server.HttpServerSupport.readJsonBody;
import static server.HttpServerSupport.writeJson;

final class SolveJobRouteHandler implements HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolveJobRouteHandler.class);
    private final SolveJobManager jobManager;
    private final AuthService authService;

    SolveJobRouteHandler(SolveJobManager jobManager, AuthService authService) {
        this.jobManager = jobManager;
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }

        try {
            var pathParts = java.util.Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                    .filter(part -> !part.isBlank())
                    .toList();
            if (pathParts.size() == 2 && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleCreate(exchange);
                return;
            }
            if (pathParts.size() == 3 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                var user = optionalAuthenticated(exchange, authService);
                writeJson(exchange, 200, JsonSupport.solveJobJson(jobManager.find(
                        pathParts.get(2), externalId(user))));
                return;
            }
            if (pathParts.size() == 3 && "DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                var user = optionalAuthenticated(exchange, authService);
                writeJson(exchange, 200, JsonSupport.solveJobJson(jobManager.cancel(
                        pathParts.get(2), externalId(user))));
                return;
            }
            writeJson(exchange, 405, JsonSupport.errorJson("Method not allowed"));
        } catch (SolveJobManager.CapacityException exception) {
            writeJson(exchange, 429, JsonSupport.errorJson(exception.getMessage()));
        } catch (SolveJobManager.AuthenticationRequiredException exception) {
            writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
        } catch (SolveJobManager.ForbiddenException exception) {
            writeJson(exchange, 403, JsonSupport.errorJson(exception.getMessage()));
        } catch (AuthService.UnauthorizedException exception) {
            writeJson(exchange, 401, JsonSupport.errorJson(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
        } catch (SolveDeadlineExceededException exception) {
            writeJson(exchange, 504, JsonSupport.errorJson(exception.getMessage()));
        } catch (Exception exception) {
            LOGGER.error("Synchronous solve request failed", exception);
            writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
        }
    }

    private void handleCreate(HttpExchange exchange) throws Exception {
        var body = readJsonBody(exchange);
        var request = new CreateSolveJobRequest(
                JsonSupport.readString(body, "scramble"),
                JsonSupport.readString(body, "crossFace"),
                JsonSupport.readString(body, "f2lMode"),
                JsonSupport.readLong(body, "solveId"),
                JsonSupport.readBoolean(body, "saveOnComplete"),
                JsonSupport.readLong(body, "deadlineSeconds")
        );
        var user = optionalAuthenticated(exchange, authService);
        if (request.saveOnComplete() && user == null) {
            throw new AuthService.UnauthorizedException("Authentication required");
        }
        var job = jobManager.submit(
                request.solveRequest(),
                externalId(user),
                request.solveId(),
                request.saveOnComplete()
        );
        writeJson(exchange, 202, JsonSupport.solveJobJson(job));
    }
}
