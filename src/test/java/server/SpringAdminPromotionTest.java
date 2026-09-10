package server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import springboot.config.AdminProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies that configured existing accounts are promoted by Spring startup. */
class SpringAdminPromotionTest {
    @Test
    void configuredAdminPromotion_updatesTheConfiguredAccount() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var dataSources = mock(ObjectProvider.class);
        var properties = new AdminProperties();
        properties.setEmail("Admin@example.com");
        when(dataSources.getIfAvailable()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.contains("SET role = 'admin'")))
                .thenReturn(statement);

        var runner = new SpringServerConfiguration().configuredAdminPromotion(dataSources, properties);
        runner.run(new org.springframework.boot.DefaultApplicationArguments());

        verify(statement).setString(1, "Admin@example.com");
        verify(statement).executeUpdate();
    }
}
