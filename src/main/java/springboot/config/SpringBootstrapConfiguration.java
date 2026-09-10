package springboot.config;

import com.zaxxer.hikari.HikariDataSource;
import database.DatabaseConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** Shared Spring foundations for database, frontend, server, and admin settings. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HikariDataSource.class)
public class SpringBootstrapConfiguration {
    @Bean
    @ConfigurationProperties("database")
    DatabaseProperties databaseProperties() {
        return DatabaseProperties.fromEnvironmentDefaults();
    }

    @Bean
    @ConfigurationProperties("frontend")
    FrontendProperties frontendProperties() {
        return FrontendProperties.fromEnvironmentDefaults();
    }

    @Bean
    @ConfigurationProperties("server")
    ServerProperties serverProperties() {
        return ServerProperties.fromEnvironmentDefaults();
    }

    @Bean
    @ConfigurationProperties("admin")
    AdminProperties adminProperties() {
        return AdminProperties.fromEnvironmentDefaults();
    }

    @Bean
    @Conditional(DatabaseConfiguredCondition.class)
    DataSource dataSource(DatabaseProperties properties) {
        var parsed = DatabaseConfig.fromConnectionString(properties.getUrl());
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(parsed.jdbcUrl());
        dataSource.setUsername(firstNonBlank(properties.getUser(), parsed.username()));
        dataSource.setPassword(firstNonBlank(properties.getPassword(), parsed.password()));
        dataSource.setMaximumPoolSize(properties.getPoolSize());
        dataSource.setMinimumIdle(0);
        dataSource.setPoolName("cube-solver-postgres");
        dataSource.setConnectionTimeout(10_000);
        return dataSource;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    static final class DatabaseConfiguredCondition implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context,
                               org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            return DatabaseConfig.fromEnvironment().configured();
        }
    }
}
