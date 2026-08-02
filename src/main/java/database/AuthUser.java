package database;

public record AuthUser(long id, String externalId, String email, String displayName, String role) {
    public AuthUser(long id, String externalId, String email, String displayName) {
        this(id, externalId, email, displayName, "user");
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
