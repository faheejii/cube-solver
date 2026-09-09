package server;

import database.DatabaseManager;
import database.persistence.entity.SpringHistoryPersistenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import solver.CfopSolveService;

import javax.sql.DataSource;
import java.sql.SQLException;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/** Creates adapters around the existing application services without changing their contracts. */
@Configuration
class SpringServerConfiguration {
    @Bean
    SpringDatabaseHealth springDatabaseHealth(ObjectProvider<DataSource> dataSources) {
        return SpringDatabaseHealth.from(dataSources);
    }

    @Bean
    ApplicationRunner configuredAdminPromotion(
            ObjectProvider<DataSource> dataSources,
            springboot.config.AdminProperties adminProperties
    ) {
        return arguments -> promoteConfiguredAdmin(dataSources.getIfAvailable(), adminProperties.getEmail());
    }

    @Bean(destroyMethod = "close")
    DatabaseManager databaseManager(ObjectProvider<DataSource> dataSources) throws java.sql.SQLException {
        var databaseManager = DatabaseManager.fromEnvironment();
        // Spring Boot's Flyway auto-configuration owns migrations when its
        // DataSource is available. The legacy manager remains available to
        // compatibility-facing adapters, but must not migrate a second time.
        if (dataSources.getIfAvailable() == null) {
            databaseManager.initialize();
        }
        return databaseManager;
    }

    private static void promoteConfiguredAdmin(DataSource dataSource, String adminEmail) throws SQLException {
        if (dataSource == null || adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     UPDATE users
                     SET role = 'admin', updated_at = NOW()
                     WHERE LOWER(email) = LOWER(?)
                     """)) {
            statement.setString(1, adminEmail);
            statement.executeUpdate();
        }
    }

    @Bean
    CfopSolveService cfopSolveService() {
        return new CfopSolveService();
    }

    @Bean
    OperationalMetrics operationalMetrics() {
        return new OperationalMetrics();
    }

    @Bean(destroyMethod = "close")
    SolveJobManager solveJobManager(
            CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence,
            OperationalMetrics operationalMetrics,
            @org.springframework.beans.factory.annotation.Qualifier("optimizedSolveExecutor") ExecutorService optimizedExecutor,
            @org.springframework.beans.factory.annotation.Qualifier("fastSolveExecutor") ExecutorService fastExecutor
    ) {
        return new SolveJobManager(
                solveService,
                completedSolutionPersistence,
                operationalMetrics,
                optimizedExecutor,
                fastExecutor
        );
    }

    @Bean
    CompletedSolutionPersistence completedSolutionPersistence(
            SpringHistoryPersistenceService history,
            ObjectProvider<DataSource> dataSources
    ) {
        return new SpringCompletedSolutionPersistence(history, dataSources.getIfAvailable());
    }

    @Bean
    RequestRateLimiter authRequestRateLimiter() {
        return new RequestRateLimiter(
                HttpServerSupport.configuredAuthRateLimit(),
                java.time.Duration.ofMinutes(1)
        );
    }

    @Bean
    Path frontendDistDirectory(@Value("${frontend.dist:frontend/dist}") String frontendDist) {
        return Path.of(frontendDist).toAbsolutePath().normalize();
    }
}
