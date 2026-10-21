package server.web;

final class ApiMethodNotAllowedException extends RuntimeException {
    ApiMethodNotAllowedException() {
        super("Method not allowed");
    }
}
