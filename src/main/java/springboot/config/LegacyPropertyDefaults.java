package springboot.config;

import config.Dotenv;

final class LegacyPropertyDefaults {
    private LegacyPropertyDefaults() {
    }

    static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static String legacyValue(Dotenv dotenv, String systemProperty, String environmentVariable) {
        return firstNonBlank(
                System.getProperty(systemProperty),
                System.getenv(environmentVariable),
                dotenv.get(environmentVariable)
        );
    }
}
