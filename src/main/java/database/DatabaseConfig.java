package database;

import config.Dotenv;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public record DatabaseConfig(
        boolean configured,
        String jdbcUrl,
        String username,
        String password
) {
    public static DatabaseConfig fromEnvironment() {
        var dotenv = Dotenv.loadDefault();
        var rawUrl = firstNonBlank(
                System.getProperty("database.url"),
                System.getenv("DATABASE_URL"),
                dotenv.get("DATABASE_URL")
        );
        if (rawUrl == null) {
            return disabled();
        }
        return fromConnectionString(rawUrl);
    }

    public static DatabaseConfig fromConnectionString(String connectionString) {
        if (connectionString == null || connectionString.isBlank()) {
            return disabled();
        }

        var normalized = connectionString.trim();
        if (normalized.startsWith("jdbc:postgresql://")) {
            var dotenv = Dotenv.loadDefault();
            return new DatabaseConfig(
                    true,
                    normalized,
                    firstNonBlank(System.getProperty("database.user"), System.getenv("DATABASE_USER"), dotenv.get("DATABASE_USER")),
                    firstNonBlank(System.getProperty("database.password"), System.getenv("DATABASE_PASSWORD"), dotenv.get("DATABASE_PASSWORD"))
            );
        }

        if (!normalized.startsWith("postgresql://")) {
            throw new IllegalArgumentException("Unsupported database URL format. Expected postgresql:// or jdbc:postgresql://");
        }

        try {
            var uri = new URI(normalized);
            var host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Database URL is missing a host");
            }

            var port = uri.getPort();
            var databaseName = uri.getPath();
            if (databaseName == null || databaseName.isBlank() || "/".equals(databaseName)) {
                throw new IllegalArgumentException("Database URL is missing a database name");
            }

            var query = uri.getRawQuery();
            var jdbcUrl = new StringBuilder("jdbc:postgresql://")
                    .append(host);
            if (port > 0) {
                jdbcUrl.append(':').append(port);
            }
            jdbcUrl.append(databaseName);
            if (query != null && !query.isBlank()) {
                jdbcUrl.append('?').append(query);
            }

            var credentials = parseUserInfo(uri.getRawUserInfo());
            return new DatabaseConfig(true, jdbcUrl.toString(), credentials.username(), credentials.password());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid database URL", exception);
        }
    }

    public static DatabaseConfig disabled() {
        return new DatabaseConfig(false, null, null, null);
    }

    private static Credentials parseUserInfo(String rawUserInfo) {
        if (rawUserInfo == null || rawUserInfo.isBlank()) {
            var dotenv = Dotenv.loadDefault();
            return new Credentials(
                    firstNonBlank(System.getProperty("database.user"), System.getenv("DATABASE_USER"), dotenv.get("DATABASE_USER")),
                    firstNonBlank(System.getProperty("database.password"), System.getenv("DATABASE_PASSWORD"), dotenv.get("DATABASE_PASSWORD"))
            );
        }

        var parts = rawUserInfo.split(":", 2);
        var username = decode(parts[0]);
        var password = parts.length > 1 ? decode(parts[1]) : "";
        return new Credentials(username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record Credentials(String username, String password) {
    }
}
