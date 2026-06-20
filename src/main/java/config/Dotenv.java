package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Dotenv {
    private final Map<String, String> values;

    private Dotenv(Map<String, String> values) {
        this.values = values;
    }

    public static Dotenv loadDefault() {
        return load(Path.of(".env"));
    }

    public static Dotenv load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return new Dotenv(Map.of());
        }

        var values = new HashMap<String, String>();
        try {
            for (var rawLine : Files.readAllLines(path)) {
                var line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                var separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                var key = line.substring(0, separatorIndex).trim();
                if (key.isEmpty()) {
                    continue;
                }

                var value = line.substring(separatorIndex + 1).trim();
                values.put(key, stripQuotes(value));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read .env file: " + path.toAbsolutePath(), exception);
        }

        return new Dotenv(Map.copyOf(values));
    }

    public String get(String key) {
        return values.get(key);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            if (value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length() - 1);
            }
            if (value.startsWith("'") && value.endsWith("'")) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
