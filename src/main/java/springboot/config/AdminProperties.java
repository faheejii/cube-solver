package springboot.config;

import config.Dotenv;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring-bound administrator bootstrap setting. */
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {
    private String email;

    static AdminProperties fromLegacyDefaults() {
        var dotenv = Dotenv.loadDefault();
        var properties = new AdminProperties();
        properties.email = LegacyPropertyDefaults.legacyValue(dotenv, "admin.email", "ADMIN_EMAIL");
        return properties;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
