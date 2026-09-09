package server;

/** Signals that an endpoint requiring persistence was called without a configured database. */
final class SpringDatabaseUnavailableException extends RuntimeException {
    SpringDatabaseUnavailableException() {
        super("Database is not configured");
    }
}
