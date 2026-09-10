package server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Canonical Spring Boot entry point for the application. */
@SpringBootApplication(scanBasePackages = {"server", "database", "springboot"})
@EnableJpaRepositories(basePackages = "database.persistence.repository")
@EntityScan(basePackages = "database.persistence.entity")
public class SpringCubeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringCubeApplication.class, args);
    }
}
