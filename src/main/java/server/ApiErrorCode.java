package server;

/** Stable machine-readable categories for HTTP API failures. */
enum ApiErrorCode {
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    CONFLICT(409),
    RATE_LIMITED(429),
    TIMEOUT(504),
    SERVICE_UNAVAILABLE(503),
    INTERNAL_ERROR(500);

    private final int status;

    ApiErrorCode(int status) {
        this.status = status;
    }

    int status() {
        return status;
    }
}
