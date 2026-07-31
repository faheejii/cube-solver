package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager implements AutoCloseable {
    public static final String SOLVER_VERSION = "cfop-web-v1";
    private final DatabaseConfig config;
    private volatile HikariDataSource dataSource;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config == null ? DatabaseConfig.disabled() : config;
    }

    public static DatabaseManager fromEnvironment() {
        return new DatabaseManager(DatabaseConfig.fromEnvironment());
    }

    public boolean isConfigured() {
        return config.configured();
    }

    public void initialize() throws SQLException {
        if (!isConfigured()) {
            return;
        }
        try {
            Flyway.configure()
                    .dataSource(dataSource())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();
        } catch (RuntimeException exception) {
            throw new SQLException("Database migration failed", exception);
        }
    }

    public DatabaseHealth health() {
        if (!isConfigured()) {
            return DatabaseHealth.disabled();
        }
        try (var connection = openConnection();
             var statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return DatabaseHealth.ok();
        } catch (SQLException exception) {
            return DatabaseHealth.error(exception.getMessage());
        }
    }

    public Connection openConnection() throws SQLException {
        if (!isConfigured()) {
            throw new IllegalStateException("Database is not configured");
        }
        return dataSource().getConnection();
    }

    @Override
    public void close() {
        var current = dataSource;
        if (current != null) {
            current.close();
            dataSource = null;
        }
    }

    private HikariDataSource dataSource() {
        var current = dataSource;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (dataSource == null) {
                var hikari = new HikariConfig();
                hikari.setJdbcUrl(config.jdbcUrl());
                hikari.setUsername(config.username());
                hikari.setPassword(config.password());
                hikari.setMaximumPoolSize(configuredPoolSize());
                hikari.setMinimumIdle(0);
                hikari.setPoolName("cube-solver-postgres");
                hikari.setConnectionTimeout(10_000);
                dataSource = new HikariDataSource(hikari);
            }
            return dataSource;
        }
    }

    private static int configuredPoolSize() {
        try {
            return Math.max(1, Integer.parseInt(System.getProperty("database.pool.size", "4")));
        } catch (NumberFormatException exception) {
            return 4;
        }
    }

}
