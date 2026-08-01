package api;

public record RegisterRequest(String email, String password, String displayName) {
    public RegisterRequest {
        email = normalizeEmail(email);
        requirePassword(password);
        displayName = normalizeDisplayName(displayName);
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }
        var normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("email is invalid");
        }
        return normalized;
    }

    private static void requirePassword(String value) {
        if (value == null || value.length() < 8 || value.length() > 1024) {
            throw new IllegalArgumentException("password must be between 8 and 1024 characters");
        }
    }

    private static String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("displayName cannot exceed 100 characters");
        }
        return normalized;
    }
}
