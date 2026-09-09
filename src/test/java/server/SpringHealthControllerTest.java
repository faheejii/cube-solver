package server;

import database.DatabaseHealth;
import database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Focused Spring MVC contract coverage for the public health routes. */
@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = SpringHealthControllerTest.MvcTestConfiguration.class)
class SpringHealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatabaseManager databaseManager;

    @MockBean
    private OperationalMetrics operationalMetrics;

    @Configuration(proxyBeanMethods = false)
    static class MvcTestConfiguration {
        @Bean
        SpringHealthController springHealthController(
                DatabaseManager databaseManager,
                OperationalMetrics operationalMetrics
        ) {
            return new SpringHealthController(databaseManager, operationalMetrics);
        }
    }

    @Test
    void liveRoute_returnsLegacyLivenessResponse() throws Exception {
        mockMvc.perform(get("/api/health/live"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void readyRoute_returnsHealthyDatabaseResponse() throws Exception {
        when(databaseManager.health()).thenReturn(DatabaseHealth.ok());

        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.database.status").value("ok"))
                .andExpect(jsonPath("$.database.message").value("connected"));
    }

    @Test
    void readyRoute_returnsServiceUnavailableWhenDatabaseIsUnavailable() throws Exception {
        when(databaseManager.health()).thenReturn(DatabaseHealth.error("connection refused"));

        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.database.status").value("error"))
                .andExpect(jsonPath("$.database.message").value("connection refused"));
    }
}
