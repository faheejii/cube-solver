package database;

public record DatabaseHealth(
        String status,
        String message
) {
    public static DatabaseHealth ok() {
        return new DatabaseHealth("ok", "connected");
    }

    public static DatabaseHealth disabled() {
        return new DatabaseHealth("disabled", "DATABASE_URL not configured");
    }

    public static DatabaseHealth error(String message) {
        return new DatabaseHealth("error", message);
    }
}
