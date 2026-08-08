package server;

import api.SolveApiRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import solver.SolveDeadlineExceededException;

import java.io.IOException;

import static server.HttpServerSupport.handleCors;
import static server.HttpServerSupport.readJsonBody;
import static server.HttpServerSupport.writeJson;

final class SolveRouteHandler implements HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolveRouteHandler.class);
    private final SolveJobManager jobManager;

    SolveRouteHandler(SolveJobManager jobManager) {
        this.jobManager = jobManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, JsonSupport.errorJson("Method not allowed"));
            return;
        }

        try {
            var body = readJsonBody(exchange);
            var request = new SolveApiRequest(
                    JsonSupport.readString(body, "scramble"),
                    JsonSupport.readString(body, "crossFace"),
                    JsonSupport.readString(body, "f2lMode")
            );
            var job = jobManager.submit(request, null, null, false);
            while (true) {
                var snapshot = jobManager.find(job.id());
                if ("completed".equals(snapshot.status()) && snapshot.result() != null) {
                    writeJson(exchange, 200, JsonSupport.solveResultJson(snapshot.result()));
                    return;
                }
                if ("timed_out".equals(snapshot.status())) {
                    writeJson(exchange, 504, JsonSupport.errorJson(snapshot.error()));
                    return;
                }
                if ("failed".equals(snapshot.status())) {
                    writeJson(exchange, 500, JsonSupport.errorJson(snapshot.error()));
                    return;
                }
                if ("cancelled".equals(snapshot.status())) {
                    writeJson(exchange, 409, JsonSupport.errorJson(snapshot.error()));
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    jobManager.cancel(job.id());
                    throw new IOException("Solve request interrupted", exception);
                }
            }
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, JsonSupport.errorJson(exception.getMessage()));
        } catch (SolveJobManager.CapacityException exception) {
            writeJson(exchange, 429, JsonSupport.errorJson(exception.getMessage()));
        } catch (SolveDeadlineExceededException exception) {
            writeJson(exchange, 504, JsonSupport.errorJson(exception.getMessage()));
        } catch (Exception exception) {
            LOGGER.error("Solve request failed", exception);
            writeJson(exchange, 500, JsonSupport.errorJson("Internal server error"));
        }
    }
}
