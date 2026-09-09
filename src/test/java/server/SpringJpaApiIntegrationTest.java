package server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import test.PostgresTestDatabase;

import jakarta.servlet.http.Cookie;
import java.sql.SQLException;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PostgreSQL-backed contract coverage for the Spring/JPA API path. */
@SpringBootTest(
        classes = SpringCubeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "server.cookie.secure=false",
                "server.cors.origin=http://localhost:5173"
        }
)
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class SpringJpaApiIntegrationTest {
    private static final String PASSWORD = "integration-password";
    private static PostgresTestDatabase postgres;
    private static String schemaUrl;
    private static String previousDatabaseUrl;

    @Autowired
    private MockMvc mockMvc;

    private String sessionToken;

    @DynamicPropertySource
    static void registerDatabase(DynamicPropertyRegistry registry) throws Exception {
        postgres = PostgresTestDatabase.create();
        postgres.manager().initialize();
        schemaUrl = System.getenv("TEST_DATABASE_URL")
                + (System.getenv("TEST_DATABASE_URL").contains("?") ? "&" : "?")
                + "currentSchema=" + postgres.schema();
        previousDatabaseUrl = System.getProperty("database.url");
        System.setProperty("database.url", schemaUrl);
        registry.add("database.url", () -> schemaUrl);
    }

    @AfterAll
    static void closeDatabase() throws SQLException {
        if (previousDatabaseUrl == null) {
            System.clearProperty("database.url");
        } else {
            System.setProperty("database.url", previousDatabaseUrl);
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void clearSession() {
        sessionToken = null;
    }

    @Test
    void registrationLoginSessionAndLogout_shouldUseJpaAuthentication() throws Exception {
        var email = uniqueEmail("auth");
        var registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is(email)))
                .andReturn();
        captureSession(registration);

        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.role", is("user")));

        mockMvc.perform(post("/api/auth/logout").cookie(sessionCookie()))
                .andExpect(status().isNoContent());
        sessionToken = null;
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        var login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)))
                .andReturn();
        captureSession(login);
        mockMvc.perform(get("/api/auth/me").cookie(sessionCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void historySolutionsStatisticsAndDelete_shouldUseJpaPersistence() throws Exception {
        loginAs(uniqueEmail("history"));
        var attemptId = "attempt-" + UUID.randomUUID();
        var solve = createSolve(attemptId, 1250, false);
        var solveId = jsonLong(solve, "id");

        mockMvc.perform(post("/api/solves")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientAttemptId":"%s","scramble":"R U","crossFaceRequested":"U",
                                 "timerMs":1250,"penalty":"none","officialMs":1250,"dnf":false}
                                """.formatted(attemptId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is((int) solveId)));

        mockMvc.perform(get("/api/solves").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].clientAttemptId", is(attemptId)));
        mockMvc.perform(get("/api/solves/" + solveId).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solutions", hasSize(0)));

        mockMvc.perform(put("/api/solves/" + solveId + "/solutions/greedy")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solutionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode", is("greedy")))
                .andExpect(jsonPath("$.crossFace", is("U")));
        mockMvc.perform(get("/api/solves/" + solveId).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solutions", hasSize(1)))
                .andExpect(jsonPath("$.solutions[0].mode", is("greedy")));

        createSolve("attempt-" + UUID.randomUUID(), 1400, false);
        createSolve("attempt-" + UUID.randomUUID(), null, true);
        mockMvc.perform(get("/api/stats").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solveCount", is(3)))
                .andExpect(jsonPath("$.dnfCount", is(1)))
                .andExpect(jsonPath("$.bestMs", is(1250)))
                .andExpect(jsonPath("$.averageMs", is(1325)));

        mockMvc.perform(delete("/api/solves/" + solveId).cookie(sessionCookie()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/solves/" + solveId).cookie(sessionCookie()))
                .andExpect(status().isNotFound());
    }

    @Test
    void solveJobCompletion_shouldPersistSolutionThroughJpa() throws Exception {
        loginAs(uniqueEmail("job"));
        var solve = createSolve("attempt-" + UUID.randomUUID(), 900, false);
        var solveId = jsonLong(solve, "id");
        var job = mockMvc.perform(post("/api/solve-jobs")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scramble":"R","crossFace":"F","f2lMode":"greedy",
                                 "solveId":%d,"saveOnComplete":true,"deadlineSeconds":30}
                                """.formatted(solveId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn();
        var jobId = jsonString(job, "id");

        MvcResult latest = null;
        String state = null;
        for (int attempt = 0; attempt < 120; attempt++) {
            latest = mockMvc.perform(get("/api/solve-jobs/" + jobId).cookie(sessionCookie())).andReturn();
            state = jsonString(latest, "status");
            if ("completed".equals(state) || "failed".equals(state) || "timed_out".equals(state)) {
                break;
            }
            Thread.sleep(250);
        }
        assertNotNull(latest);
        assertTrue("completed".equals(state), latest.getResponse().getContentAsString());
        mockMvc.perform(get("/api/solves/" + solveId).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solutions", hasSize(1)))
                .andExpect(jsonPath("$.solutions[0].mode", is("greedy")));
    }

    private void loginAs(String email) throws Exception {
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        captureSession(result);
    }

    private MvcResult createSolve(String attemptId, Integer officialMs, boolean dnf) throws Exception {
        var official = officialMs == null ? "null" : officialMs.toString();
        return mockMvc.perform(post("/api/solves")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientAttemptId":"%s","scramble":"R U","crossFaceRequested":"U",
                                 "timerMs":%s,"penalty":"%s","officialMs":%s,"dnf":%s}
                                """.formatted(attemptId, official, dnf ? "dnf" : "none", official, dnf)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void captureSession(MvcResult result) throws Exception {
        var cookie = result.getResponse().getCookie(SessionCookie.NAME);
        assertNotNull(cookie, result.getResponse().getContentAsString());
        sessionToken = cookie.getValue();
    }

    private Cookie sessionCookie() {
        return new Cookie(SessionCookie.NAME, sessionToken);
    }

    private static String registerBody(String email) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Integration\"}"
                .formatted(email, PASSWORD);
    }

    private static String loginBody(String email) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD);
    }

    private static String solutionBody() {
        return """
                {"crossFaceRequested":"U","crossFaceChosen":"U","f2lMode":"greedy",
                 "f2lSetupCaseCount":0,"f2lInsertCaseCount":0,"solvedF2LSlots":"",
                 "totalMoves":4,"fullySolved":true,"solveElapsedMs":12.5,
                 "crossAlgorithm":"R U","crossMoves":2,"crossSolved":true,"crossStatus":"solved",
                 "f2lAlgorithm":"","f2lMoves":0,"f2lSolved":true,"f2lStatus":"solved",
                 "ollAlgorithm":"","ollMoves":0,"ollSolved":true,"ollStatus":"solved",
                 "pllAlgorithm":"","pllMoves":0,"pllSolved":true,"pllStatus":"solved",
                 "f2lTraceJson":{"traceComplete":true},"comparisonJson":null}
                """;
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static long jsonLong(MvcResult result, String field) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get(field).longValue();
    }

    private static String jsonString(MvcResult result, String field) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get(field).textValue();
    }
}
