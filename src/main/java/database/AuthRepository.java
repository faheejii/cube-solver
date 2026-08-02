package database;

import java.sql.SQLException;
import java.time.OffsetDateTime;

public final class AuthRepository {
    private final DatabaseManager databaseManager;
    private final String adminEmail;

    public AuthRepository(DatabaseManager databaseManager) {
        this(databaseManager, DatabaseConfig.adminEmailFromEnvironment());
    }

    public AuthRepository(DatabaseManager databaseManager, String adminEmail) {
        this.databaseManager = databaseManager;
        this.adminEmail = adminEmail;
    }

    public AuthUser createUserWithSession(
            String externalId,
            String email,
            String displayName,
            String passwordHash,
            String passwordSalt,
            int passwordIterations,
            String tokenHash,
            OffsetDateTime expiresAt
    ) throws SQLException {
        try (var connection = databaseManager.openConnection()) {
            connection.setAutoCommit(false);
            try {
                long userId;
                try (var statement = connection.prepareStatement("""
                        INSERT INTO users (
                            external_id, email, display_name,
                            password_hash, password_salt, password_iterations, role
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        RETURNING id, role
                        """)) {
                    statement.setString(1, externalId);
                    statement.setString(2, email);
                    statement.setString(3, displayName);
                    statement.setString(4, passwordHash);
                    statement.setString(5, passwordSalt);
                    statement.setInt(6, passwordIterations);
                    statement.setString(7, isAdminEmail(email) ? "admin" : "user");
                    try (var result = statement.executeQuery()) {
                        result.next();
                        userId = result.getLong("id");
                        var role = result.getString("role");
                        insertSession(connection, userId, tokenHash, expiresAt);
                        connection.commit();
                        return new AuthUser(userId, externalId, email, displayName, role);
                    }
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public StoredCredential findCredential(String email) throws SQLException {
        try (var connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                     SELECT id, external_id, email, display_name,
                            password_hash, password_salt, password_iterations, role
                     FROM users
                     WHERE LOWER(email) = LOWER(?) AND password_hash IS NOT NULL
                     """)) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                var user = new AuthUser(
                        result.getLong("id"),
                        result.getString("external_id"),
                        result.getString("email"),
                        result.getString("display_name"),
                        result.getString("role")
                );
                return new StoredCredential(
                        user,
                        result.getString("password_hash"),
                        result.getString("password_salt"),
                        result.getInt("password_iterations")
                );
            }
        }
    }

    public void createSession(long userId, String tokenHash, OffsetDateTime expiresAt) throws SQLException {
        try (var connection = databaseManager.openConnection()) {
            insertSession(connection, userId, tokenHash, expiresAt);
        }
    }

    private static void insertSession(
            java.sql.Connection connection,
            long userId,
            String tokenHash,
            OffsetDateTime expiresAt
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO auth_sessions (user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.setObject(3, expiresAt);
            statement.executeUpdate();
        }
    }

    public AuthUser findSessionUser(String tokenHash, OffsetDateTime now) throws SQLException {
        try (var connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("""
                     UPDATE auth_sessions session
                     SET last_seen_at = ?
                     FROM users account
                     WHERE session.token_hash = ?
                       AND session.expires_at > ?
                       AND session.user_id = account.id
                     RETURNING account.id, account.external_id, account.email, account.display_name, account.role
                     """)) {
            statement.setObject(1, now);
            statement.setString(2, tokenHash);
            statement.setObject(3, now);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new AuthUser(
                        result.getLong("id"),
                        result.getString("external_id"),
                        result.getString("email"),
                        result.getString("display_name"),
                        result.getString("role")
                );
            }
        }
    }

    public void deleteSession(String tokenHash) throws SQLException {
        try (var connection = databaseManager.openConnection();
             var statement = connection.prepareStatement("DELETE FROM auth_sessions WHERE token_hash = ?")) {
            statement.setString(1, tokenHash);
            statement.executeUpdate();
        }
    }

    private boolean isAdminEmail(String email) {
        return adminEmail != null && email != null && adminEmail.equalsIgnoreCase(email.trim());
    }
}
