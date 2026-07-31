package api;

public record LoginRequest(String email, String password) {
    public LoginRequest {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("email and password are required");
        }
        email = email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
