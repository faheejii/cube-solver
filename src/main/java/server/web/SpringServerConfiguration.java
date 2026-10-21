package server.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springboot.config.ServerProperties;
import springboot.config.FrontendProperties;
import solver.CfopSolveService;

import javax.sql.DataSource;
import java.nio.file.Path;
import server.solve.SolveJobSaveCommandFactory;
import server.solve.SolverProfileService;

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

    private static void promoteConfiguredAdmin(DataSource dataSource, String adminEmail) throws java.sql.SQLException {
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
    SolverProfileService solverProfileService(CfopSolveService solveService) {
        return new SolverProfileService(solveService);
    }

    @Bean
    OperationalMetrics operationalMetrics() {
        return new OperationalMetrics();
    }

    @Bean
    RequestRateLimiter authRequestRateLimiter(ServerProperties serverProperties) {
        return new RequestRateLimiter(
                serverProperties.getAuthRequestsPerMinute(),
                java.time.Duration.ofMinutes(1)
        );
    }

    @Bean
    Path frontendDistDirectory(FrontendProperties frontendProperties) {
        return frontendProperties.distPath();
    }

    @Bean
    SolveJobSaveCommandFactory solveJobSaveCommandFactory() {
        return new JsonSolveJobSaveCommandFactory();
    }
}
