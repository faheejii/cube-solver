package test;

import database.DatabaseConfig;
import database.DatabaseManager;

import java.sql.SQLException;
import java.util.UUID;

/** Provides an isolated PostgreSQL schema for integration tests. */
public final class PostgresTestDatabase implements AutoCloseable {
    private final DatabaseConfig baseConfig;
    private final String schema;
    private final DatabaseManager manager;

    private PostgresTestDatabase(DatabaseConfig baseConfig, String schema, DatabaseManager manager) {
        this.baseConfig = baseConfig;
        this.schema = schema;
        this.manager = manager;
    }

    public static PostgresTestDatabase create() throws SQLException {
        var baseConfig = DatabaseConfig.fromConnectionString(System.getenv("TEST_DATABASE_URL"));
        var schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (var base = new DatabaseManager(baseConfig); var connection = base.openConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
        }

        var rawUrl = System.getenv("TEST_DATABASE_URL");
        var schemaUrl = rawUrl + (rawUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        return new PostgresTestDatabase(baseConfig, schema,
                new DatabaseManager(DatabaseConfig.fromConnectionString(schemaUrl)));
    }

    public DatabaseManager manager() {
        return manager;
    }

    public String schema() {
        return schema;
    }

    @Override
    public void close() throws SQLException {
        manager.close();
        try (var base = new DatabaseManager(baseConfig); var connection = base.openConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
    }
}
