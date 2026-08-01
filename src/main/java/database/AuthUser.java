package database;

public record AuthUser(long id, String externalId, String email, String displayName) {
}
