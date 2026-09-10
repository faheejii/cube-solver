package test;

import database.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import java.sql.SQLException;
import java.sql.Connection;
import java.util.UUID;

/** Provides an isolated PostgreSQL schema for integration tests. */
public final class PostgresTestDatabase implements AutoCloseable {
    private final DatabaseConfig baseConfig;
    private final String schema;
    private final HikariDataSource dataSource;

    private PostgresTestDatabase(DatabaseConfig baseConfig, String schema, HikariDataSource dataSource) {
        this.baseConfig = baseConfig;
        this.schema = schema;
        this.dataSource = dataSource;
    }

    public static PostgresTestDatabase create() throws SQLException {
        var baseConfig = DatabaseConfig.fromConnectionString(System.getenv("TEST_DATABASE_URL"));
        var schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (var base = dataSource(baseConfig); var connection = base.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
        }

        var rawUrl = System.getenv("TEST_DATABASE_URL");
        var schemaUrl = rawUrl + (rawUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        return new PostgresTestDatabase(baseConfig, schema,
                dataSource(DatabaseConfig.fromConnectionString(schemaUrl)));
    }

    public void initialize() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    public Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String schema() {
        return schema;
    }

    @Override
    public void close() throws SQLException {
        dataSource.close();
        try (var base = dataSource(baseConfig); var connection = base.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
        }
    }

    private static HikariDataSource dataSource(DatabaseConfig config) {
        var hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(4);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(10_000);
        return new HikariDataSource(hikari);
    }
}
