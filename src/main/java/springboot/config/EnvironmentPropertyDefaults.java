package springboot.config;

import config.Dotenv;

/** Resolves Spring settings from JVM properties, environment, and root .env values. */
final class EnvironmentPropertyDefaults {
    private EnvironmentPropertyDefaults() {
    }

    static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String value(Dotenv dotenv, String systemProperty, String environmentVariable) {
        return firstNonBlank(
                System.getProperty(systemProperty),
                System.getenv(environmentVariable),
                dotenv.get(environmentVariable)
        );
    }
}
