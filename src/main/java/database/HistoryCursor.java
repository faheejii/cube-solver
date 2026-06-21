package database;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record HistoryCursor(
        OffsetDateTime createdAt,
        long id
) {
    public static HistoryCursor parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var parts = value.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid history cursor");
        }
        try {
            return new HistoryCursor(OffsetDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid history cursor", exception);
        }
    }

    public String encode() {
        return createdAt + "|" + id;
    }
}
