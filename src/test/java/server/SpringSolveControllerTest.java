package server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import solver.CfopSolveResult;
import solver.CfopStageResult;
import solver.F2LSolveTrace;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MVC contract coverage for the synchronous solve endpoint. */
@WebMvcTest(controllers = SpringSolveController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = SpringSolveControllerTest.MvcTestConfiguration.class)
class SpringSolveControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolveJobManager jobManager;

    @Configuration(proxyBeanMethods = false)
    static class MvcTestConfiguration {
        @Bean
        SpringSolveController springSolveController(SolveJobManager jobManager) {
            return new SpringSolveController(jobManager);
        }

        @Bean
        SpringExceptionHandler springExceptionHandler() {
            return new SpringExceptionHandler();
        }
    }

    @Test
    void solve_returnsTheCompletedResult() throws Exception {
        var snapshot = snapshot("solve-1", "completed", result(), null);
        when(jobManager.submit(any(), isNull(), isNull(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(snapshot);
        when(jobManager.find("solve-1")).thenReturn(snapshot);

        mockMvc.perform(post("/api/solve")
                        .contentType("application/json")
                        .content("{\"scramble\":\"R U\",\"crossFace\":\"U\",\"f2lMode\":\"greedy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scramble").value("R U"))
                .andExpect(jsonPath("$.fullySolved").value(true));
    }

    @Test
    void solve_mapsTimeoutToGatewayTimeout() throws Exception {
        var snapshot = snapshot("solve-2", "timed_out", null, "deadline exceeded");
        when(jobManager.submit(any(), isNull(), isNull(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(snapshot);
        when(jobManager.find("solve-2")).thenReturn(snapshot);

        mockMvc.perform(post("/api/solve")
                        .contentType("application/json")
                        .content("{\"scramble\":\"R\",\"crossFace\":\"U\",\"f2lMode\":\"greedy\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error").value("deadline exceeded"))
                .andExpect(jsonPath("$.code").value("TIMEOUT"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void solve_rejectsInvalidRequestWithoutSubmittingAJob() throws Exception {
        mockMvc.perform(post("/api/solve")
                        .contentType("application/json")
                        .content("{\"scramble\":\"R\",\"crossFace\":\"U\",\"deadlineSeconds\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.requestId").exists());

        verify(jobManager, never()).submit(any(), any(), any(), any(Boolean.class));
    }

    private static SolveJobManager.JobSnapshot snapshot(
            String id, String status, CfopSolveResult result, String error
    ) {
        return new SolveJobManager.JobSnapshot(
                id, status, 0, 0, 0, -1, 0, 0, -1,
                "QUEUED", "U", 0, 1, 0, 0, false, result, error
        );
    }

    private static CfopSolveResult result() {
        var stage = new CfopStageResult("stage", "", 0, true, "solved");
        return new CfopSolveResult(
                "R U", "U", "greedy", 0, 0, stage, stage, stage, stage,
                "FR,FL,BL,BR", true, 1.0, null, new F2LSolveTrace(List.of(), List.of(), true)
        );
    }
}
