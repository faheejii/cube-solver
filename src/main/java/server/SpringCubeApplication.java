package server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Canonical Spring Boot entry point for the application. */
@SpringBootApplication(scanBasePackages = {"server", "database", "springboot"})
@EnableJpaRepositories(basePackages = {"database.persistence.repository", "database.algorithms"})
@EntityScan(basePackages = {"database.persistence.entity", "database.algorithms"})
public class SpringCubeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringCubeApplication.class, args);
    }
}
