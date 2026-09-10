package springboot.config;

import config.Dotenv;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** Spring-bound location of the built frontend served by the application. */
@ConfigurationProperties(prefix = "frontend")
public class FrontendProperties {
    private String dist = "frontend/dist";

    static FrontendProperties fromEnvironmentDefaults() {
        var dotenv = Dotenv.loadDefault();
        var properties = new FrontendProperties();
        var configured = EnvironmentPropertyDefaults.value(dotenv, "frontend.dist", "FRONTEND_DIST");
        if (configured != null) {
            properties.dist = configured;
        }
        return properties;
    }

    public String getDist() {
        return dist;
    }

    public void setDist(String dist) {
        this.dist = dist;
    }

    public Path distPath() {
        return Path.of(dist).toAbsolutePath().normalize();
    }
}
