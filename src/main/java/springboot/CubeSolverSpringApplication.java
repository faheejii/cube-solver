package springboot;

import server.SpringCubeApplication;

/**
 * Compatibility launcher for the Spring Boot server.
 *
 * <p>The production application now lives in the {@code server} package so
 * that its component scan includes the HTTP adapters and persistence
 * configuration. Keep this class for callers that used the initial migration
 * entry point, but delegate to the canonical application.</p>
 */
public class CubeSolverSpringApplication {
    public static void main(String[] args) {
        SpringCubeApplication.main(args);
    }
}
