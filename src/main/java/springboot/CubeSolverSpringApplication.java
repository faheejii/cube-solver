package springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Additive Spring Boot entry point for the server migration.
 *
 * <p>The existing {@code server.ApiServerMain} remains the production entry
 * point until the route handlers are migrated into Spring MVC.</p>
 */
@SpringBootApplication(scanBasePackages = "springboot")
public class CubeSolverSpringApplication {
    public static void main(String[] args) {
        SpringApplication.run(CubeSolverSpringApplication.class, args);
    }
}
