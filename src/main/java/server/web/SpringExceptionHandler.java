package server.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Translates application failures to the existing JSON error/status contract. */
@RestControllerAdvice
final class SpringExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringExceptionHandler.class);

    @ExceptionHandler(server.auth.AuthOperations.UnauthorizedException.class)
    ResponseEntity<String> unauthorized(server.auth.AuthOperations.UnauthorizedException exception) {
        return SpringRequestSupport.error(401, exception.getMessage());
    }

    @ExceptionHandler(server.auth.AuthOperations.ConflictException.class)
    ResponseEntity<String> conflict(server.auth.AuthOperations.ConflictException exception) {
        return SpringRequestSupport.error(409, exception.getMessage());
    }

    @ExceptionHandler({server.auth.AuthOperations.ForbiddenException.class,
            server.solve.SolveJobOperations.ForbiddenException.class})
    ResponseEntity<String> forbidden(RuntimeException exception) {
        return SpringRequestSupport.error(403, exception.getMessage());
    }

    @ExceptionHandler({ApiMethodNotAllowedException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<String> methodNotAllowed(Exception exception) {
        return SpringRequestSupport.error(405, "Method not allowed");
    }

    @ExceptionHandler(server.solve.SolveJobOperations.CapacityException.class)
    ResponseEntity<String> capacity(server.solve.SolveJobOperations.CapacityException exception) {
        return SpringRequestSupport.error(429, exception.getMessage());
    }

    @ExceptionHandler(SpringAuthController.RateLimitExceededException.class)
    ResponseEntity<String> rateLimited(SpringAuthController.RateLimitExceededException exception) {
        return SpringRequestSupport.error(429, exception.getMessage());
    }

    @ExceptionHandler(SpringDatabaseUnavailableException.class)
    ResponseEntity<String> databaseUnavailable(SpringDatabaseUnavailableException exception) {
        return SpringRequestSupport.error(503, exception.getMessage());
    }

    @ExceptionHandler(server.solve.SolveJobOperations.AuthenticationRequiredException.class)
    ResponseEntity<String> jobAuthentication(
            server.solve.SolveJobOperations.AuthenticationRequiredException exception
    ) {
        return SpringRequestSupport.error(401, exception.getMessage());
    }

    @ExceptionHandler({solver.SolveDeadlineExceededException.class, solver.SolveBudgetExceededException.class})
    ResponseEntity<String> timeout(RuntimeException exception) {
        return SpringRequestSupport.error(504, exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<String> badRequest(Exception exception) {
        if (exception instanceof IllegalArgumentException
                && ("Solve not found".equals(exception.getMessage())
                || "Solve job not found".equals(exception.getMessage()))) {
            return SpringRequestSupport.error(404, exception.getMessage());
        }
        return SpringRequestSupport.error(400, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<String> internal(Exception exception) {
        LOGGER.error("Spring API request failed", exception);
        return SpringRequestSupport.error(500, "Internal server error");
    }
}
