package server;

import database.DatabaseManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import solver.CfopSolveService;

import java.nio.file.Path;

/** Creates adapters around the existing application services without changing their contracts. */
@Configuration
class SpringServerConfiguration {
    @Bean(destroyMethod = "close")
    DatabaseManager databaseManager() throws java.sql.SQLException {
        var databaseManager = DatabaseManager.fromEnvironment();
        databaseManager.initialize();
        return databaseManager;
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
            DatabaseManager databaseManager,
            OperationalMetrics operationalMetrics
    ) {
        return new SolveJobManager(solveService, databaseManager, operationalMetrics);
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
