package server;

final class ApiMethodNotAllowedException extends RuntimeException {
    ApiMethodNotAllowedException() {
        super("Method not allowed");
    }
}
