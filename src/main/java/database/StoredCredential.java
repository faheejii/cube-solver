package database;

public record StoredCredential(
        AuthUser user,
        String passwordHash,
        String passwordSalt,
        int passwordIterations
) {
}
