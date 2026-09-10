package server;

import database.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC contract coverage for the Spring adapter around asynchronous solve jobs. */
@WebMvcTest(controllers = SpringSolveJobController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = SpringSolveJobControllerTest.MvcTestConfiguration.class)
class SpringSolveJobControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolveJobManager jobManager;

    @Configuration(proxyBeanMethods = false)
    static class MvcTestConfiguration {
        @Bean
        SpringSolveJobController springSolveJobController(SolveJobManager jobManager) {
            return new SpringSolveJobController(jobManager);
        }

        @Bean
        SpringExceptionHandler springExceptionHandler() {
            return new SpringExceptionHandler();
        }

        @Bean
        static BeanPostProcessor controllerParameterNames() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof RequestMappingHandlerAdapter adapter) {
                        adapter.setParameterNameDiscoverer(new ParameterNameDiscoverer() {
                            @Override
                            public String[] getParameterNames(java.lang.reflect.Method method) {
                                if (method.getDeclaringClass() == SpringSolveJobController.class
                                        && method.getParameterCount() == 1
                                        && (method.getName().equals("find") || method.getName().equals("cancel"))) {
                                    return new String[]{"jobId"};
                                }
                                return null;
                            }

                            @Override
                            public String[] getParameterNames(java.lang.reflect.Constructor<?> constructor) {
                                return null;
                            }
                        });
                    }
                    return bean;
                }
            };
        }
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_delegatesRequestAndReturnsAcceptedJobSnapshot() throws Exception {
        var user = authenticatedUser();
        authenticateAs(user);
        when(jobManager.submit(any(), eq(user.externalId()), eq(42L), eq(true)))
                .thenReturn(snapshot("job-1", "queued"));

        mockMvc.perform(post("/api/solve-jobs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "scramble":"R U R'",
                                  "crossFace":"U",
                                  "f2lMode":"optimized",
                                  "solveId":42,
                                  "saveOnComplete":true,
                                  "deadlineSeconds":120,
                                  "deepColorNeutral":true
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("job-1"))
                .andExpect(jsonPath("$.status").value("queued"));

        verify(jobManager).submit(any(), eq(user.externalId()), eq(42L), eq(true));
    }

    @Test
    void create_rejectsSaveRequestWithoutAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/solve-jobs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "scramble":"R U R'",
                                  "crossFace":"U",
                                  "f2lMode":"greedy",
                                  "saveOnComplete":true
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authentication required"));

        verify(jobManager, never()).submit(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void find_andCancel_delegateJobIdAndAuthenticatedOwner() throws Exception {
        var user = authenticatedUser();
        authenticateAs(user);
        when(jobManager.find("job-2", user.externalId())).thenReturn(snapshot("job-2", "running"));
        when(jobManager.cancel("job-2", user.externalId())).thenReturn(snapshot("job-2", "cancelled"));

        mockMvc.perform(get("/api/solve-jobs/job-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("job-2"))
                .andExpect(jsonPath("$.status").value("running"));
        mockMvc.perform(delete("/api/solve-jobs/job-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("job-2"))
                .andExpect(jsonPath("$.status").value("cancelled"));

        verify(jobManager).find("job-2", user.externalId());
        verify(jobManager).cancel("job-2", user.externalId());
    }

    @Test
    void find_passesAnonymousOwnerToManager() throws Exception {
        when(jobManager.find("job-3", null)).thenReturn(snapshot("job-3", "queued"));

        mockMvc.perform(get("/api/solve-jobs/job-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("job-3"))
                .andExpect(jsonPath("$.status").value("queued"));

        verify(jobManager).find("job-3", null);
    }

    private static AuthUser authenticatedUser() {
        return new AuthUser(7L, "user-7", "user@example.com", "Test User");
    }

    private static void authenticateAs(AuthUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );
    }

    private static SolveJobManager.JobSnapshot snapshot(String id, String status) {
        return new SolveJobManager.JobSnapshot(
                id, status, 0, 0, 0, -1, 0, 0, -1,
                "QUEUED", "U", 0, 1, 0, 0, false, null, null
        );
    }
}
