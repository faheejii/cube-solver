package server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point for the HTTP adapter during the server migration. */
@SpringBootApplication
public class SpringCubeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringCubeApplication.class, args);
    }
}
