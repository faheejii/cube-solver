package springboot.config;

import config.Dotenv;

/** Spring-bound administrator bootstrap setting. */
public class AdminProperties {
    private String email;

    static AdminProperties fromEnvironmentDefaults() {
        var dotenv = Dotenv.loadDefault();
        var properties = new AdminProperties();
        properties.email = EnvironmentPropertyDefaults.value(dotenv, "admin.email", "ADMIN_EMAIL");
        return properties;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
