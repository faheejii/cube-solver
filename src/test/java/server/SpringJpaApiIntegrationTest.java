package server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import test.PostgresTestDatabase;
import database.persistence.repository.AuthSessionJpaRepository;
import database.persistence.repository.UserJpaRepository;

import jakarta.servlet.http.Cookie;
import java.sql.SQLException;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Autowired
    private ApplicationContext applicationContext;

    private String sessionToken;

    @DynamicPropertySource
    static void registerDatabase(DynamicPropertyRegistry registry) throws Exception {
        postgres = PostgresTestDatabase.create();
        postgres.initialize();
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
    void fullSpringContext_shouldDiscoverJpaRepositories() {
        assertNotNull(applicationContext.getBean(UserJpaRepository.class));
        assertNotNull(applicationContext.getBean(AuthSessionJpaRepository.class));
    }

    @Test
    void synchronousSolveRoute_isNoLongerRegistered() throws Exception {
        mockMvc.perform(post("/api/solve"))
                .andExpect(status().isMethodNotAllowed());
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
                .andExpect(jsonPath("$.solutions[0].mode", is("greedy")))
                .andExpect(jsonPath("$.solutions[0].f2l.traceComplete", is(true)));

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

    @Test
    void history_shouldBeOwnerScopedAndSupportCursorPagination() throws Exception {
        var owner = uniqueEmail("history-owner");
        loginAs(owner);
        var first = createSolve("attempt-" + UUID.randomUUID(), 1000, false);
        createSolve("attempt-" + UUID.randomUUID(), 1100, false);
        createSolve("attempt-" + UUID.randomUUID(), 1200, false);
        var firstId = jsonLong(first, "id");

        var firstPage = mockMvc.perform(get("/api/solves?limit=2").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andReturn();
        var cursor = jsonString(firstPage, "nextCursor");
        assertNotNull(cursor);
        mockMvc.perform(get("/api/solves")
                        .param("limit", "2")
                        .param("cursor", cursor)
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        loginAs(uniqueEmail("history-other"));
        mockMvc.perform(get("/api/solves/" + firstId).cookie(sessionCookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.requestId", notNullValue()));
        mockMvc.perform(delete("/api/solves/" + firstId).cookie(sessionCookie()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/solves").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void filteredHistory_shouldSearchLiterallyFilterPenaltiesAndPreserveCursorOrdering() throws Exception {
        loginAs(uniqueEmail("filtered-history-owner"));
        var none = createSolve("attempt-" + UUID.randomUUID(), "target R U", "none", 1000);
        var plusTwoOlder = createSolve("attempt-" + UUID.randomUUID(), "TARGET F R", "+2", 1200);
        var dnf = createSolve("attempt-" + UUID.randomUUID(), "Target L D", "dnf", 1300);
        var literal = createSolve("attempt-" + UUID.randomUUID(), "literal %_\\ marker", "+2", 1400);
        var plusTwoNewer = createSolve("attempt-" + UUID.randomUUID(), "target U F", "+2", 1500);
        createSolve("attempt-" + UUID.randomUUID(), "unrelated scramble", "none", 1600);

        mockMvc.perform(get("/api/stats").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentSolves", hasSize(5)))
                .andExpect(jsonPath("$.recentSolves[0].scramble", is("unrelated scramble")));

        mockMvc.perform(get("/api/solves")
                        .param("q", "  TaRgEt  ")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(4)))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[*].id", contains(
                        (int) jsonLong(plusTwoNewer, "id"),
                        (int) jsonLong(dnf, "id"),
                        (int) jsonLong(plusTwoOlder, "id"),
                        (int) jsonLong(none, "id"))))
                .andReturn();

        mockMvc.perform(get("/api/solves")
                        .param("q", "%_\\")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(literal, "id"))));

        mockMvc.perform(get("/api/solves").param("penalty", "none").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(2)))
                .andExpect(jsonPath("$.items[*].penalty", contains("none", "none")));
        mockMvc.perform(get("/api/solves").param("penalty", "+2").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andExpect(jsonPath("$.items[*].penalty", contains("+2", "+2", "+2")));
        mockMvc.perform(get("/api/solves").param("penalty", "dnf").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items[0].penalty", is("dnf")));

        var firstFilteredPage = mockMvc.perform(get("/api/solves")
                        .param("q", "target")
                        .param("penalty", "+2")
                        .param("limit", "1")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(2)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(plusTwoNewer, "id"))))
                .andReturn();
        var filteredCursor = jsonString(firstFilteredPage, "nextCursor");
        mockMvc.perform(get("/api/solves")
                        .param("q", "TARGET")
                        .param("penalty", "+2")
                        .param("limit", "1")
                        .param("cursor", filteredCursor)
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(2)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(plusTwoOlder, "id"))))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        mockMvc.perform(get("/api/solves")
                        .param("penalty", "maybe")
                        .cookie(sessionCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.error", is("penalty must be all, none, +2, or dnf")))
                .andExpect(jsonPath("$.requestId", notNullValue()));

        loginAs(uniqueEmail("filtered-history-other"));
        createSolve("attempt-" + UUID.randomUUID(), "TARGET R U", "+2", 1700);
        mockMvc.perform(get("/api/solves")
                        .param("q", "target")
                        .param("penalty", "+2")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].scramble", is("TARGET R U")));
    }

    @Test
    void timeFilteredHistory_shouldMatchDisplayedOfficialTimesAndPreservePaginationAndOwnership() throws Exception {
        loginAs(uniqueEmail("time-history-owner"));
        var exact = createSolve("attempt-" + UUID.randomUUID(), "exact", "none", 210);
        var endingInTwentyOne = createSolve("attempt-" + UUID.randomUUID(), "centiseconds", "none", 19_210);
        var minuteTwentyOne = createSolve("attempt-" + UUID.randomUUID(), "minute", "none", 81_000);
        var nineSeconds = createSolve("attempt-" + UUID.randomUUID(), "nine", "none", 9_570);
        var plusTwoEndingInTwentyOne = createSolve("attempt-" + UUID.randomUUID(), "plus two centiseconds", "+2", 211);
        var plusTwo = createSolve("attempt-" + UUID.randomUUID(), "plus two", "+2", 200);
        var sameOfficialWithoutPenalty = createSolve("attempt-" + UUID.randomUUID(), "no penalty", "none", 2_200);
        createSolve("attempt-" + UUID.randomUUID(), "dnf", "dnf", 12_000);

        mockMvc.perform(get("/api/solves").param("time", "0.21").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(exact, "id"))));

        var endingPatternPage = mockMvc.perform(get("/api/solves")
                        .param("time", "*.21")
                        .param("limit", "2")
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].id", contains(
                        (int) jsonLong(plusTwoEndingInTwentyOne, "id"),
                        (int) jsonLong(endingInTwentyOne, "id"))))
                .andReturn();
        var endingPatternCursor = jsonString(endingPatternPage, "nextCursor");
        mockMvc.perform(get("/api/solves")
                        .param("time", "*.21")
                        .param("limit", "2")
                        .param("cursor", endingPatternCursor)
                        .cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(3)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(exact, "id"))));

        mockMvc.perform(get("/api/solves").param("time", "*:21.*").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(minuteTwentyOne, "id"))));
        mockMvc.perform(get("/api/solves").param("time", "9.*").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(nineSeconds, "id"))));

        mockMvc.perform(get("/api/solves").param("time", "2.20").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(2)))
                .andExpect(jsonPath("$.items[*].penalty", contains("none", "+2")));
        mockMvc.perform(get("/api/solves").param("time", "2.20+").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items[0].id", is((int) jsonLong(plusTwo, "id"))))
                .andExpect(jsonPath("$.items[0].penalty", is("+2")));
        mockMvc.perform(get("/api/solves").param("time", "DNF").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items[0].penalty", is("dnf")));

        mockMvc.perform(get("/api/solves")
                        .param("q", "exact")
                        .param("time", "0.21")
                        .cookie(sessionCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("q and time cannot be used together")));
        for (var malformed : new String[]{"9.2", "9.*.", "1:2.*"}) {
            mockMvc.perform(get("/api/solves").param("time", malformed).cookie(sessionCookie()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
        }

        loginAs(uniqueEmail("time-history-other"));
        createSolve("attempt-" + UUID.randomUUID(), "other-user", "none", 210);
        mockMvc.perform(get("/api/solves").param("time", "0.21").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount", is(1)))
                .andExpect(jsonPath("$.items[0].scramble", is("other-user")))
                .andExpect(jsonPath("$.items[0].id", is(notNullValue())));
        assertTrue(jsonLong(sameOfficialWithoutPenalty, "id") > jsonLong(plusTwo, "id"));
    }

    @Test
    void solvePenalty_shouldUpdateOwnedAttemptAndRecalculateStatistics() throws Exception {
        var ownerEmail = uniqueEmail("penalty-owner");
        loginAs(ownerEmail);
        var solve = createSolve("attempt-" + UUID.randomUUID(), 1250, false);
        var solveId = jsonLong(solve, "id");
        createSolve("attempt-" + UUID.randomUUID(), 2250, false);
        var ownerSessionToken = sessionToken;

        mockMvc.perform(patch("/api/solves/{solveId}/penalty", solveId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"+2\"}"))
                .andExpect(status().isUnauthorized());

        loginAs(uniqueEmail("penalty-other"));
        mockMvc.perform(patch("/api/solves/{solveId}/penalty", solveId)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"+2\"}"))
                .andExpect(status().isNotFound());

        sessionToken = ownerSessionToken;
        mockMvc.perform(patch("/api/solves/{solveId}/penalty", solveId)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/solves/{solveId}/penalty", solveId)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":null}"))
                .andExpect(status().isBadRequest());

        var untimedSolve = mockMvc.perform(post("/api/solves")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientAttemptId":"attempt-%s","scramble":"R U","crossFaceRequested":"U",
                                 "timerMs":null,"penalty":"dnf","officialMs":null,"dnf":true}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(patch("/api/solves/{solveId}/penalty", jsonLong(untimedSolve, "id"))
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"+2\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/solves/{solveId}/penalty", solveId)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"+2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalty", is("+2")))
                .andExpect(jsonPath("$.timerMs", is(1250)))
                .andExpect(jsonPath("$.officialMs", is(3250)))
                .andExpect(jsonPath("$.dnf", is(false)));
        mockMvc.perform(get("/api/stats").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dnfCount", is(1)))
                .andExpect(jsonPath("$.bestMs", is(2250)))
                .andExpect(jsonPath("$.averageMs", is(2750)));

        mockMvc.perform(patch("/api/solves/{solveId}/penalty", solveId)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"dnf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalty", is("dnf")))
                .andExpect(jsonPath("$.officialMs").value(nullValue()))
                .andExpect(jsonPath("$.dnf", is(true)));
        mockMvc.perform(get("/api/stats").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dnfCount", is(2)))
                .andExpect(jsonPath("$.bestMs", is(2250)))
                .andExpect(jsonPath("$.averageMs", is(2250)));

        mockMvc.perform(patch("/api/solves/{solveId}/penalty", solveId)
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"none\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalty", is("none")))
                .andExpect(jsonPath("$.officialMs", is(1250)))
                .andExpect(jsonPath("$.dnf", is(false)));
        mockMvc.perform(get("/api/solves/{solveId}", solveId).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalty", is("none")))
                .andExpect(jsonPath("$.timerMs", is(1250)))
                .andExpect(jsonPath("$.officialMs", is(1250)))
                .andExpect(jsonPath("$.dnf", is(false)));
        mockMvc.perform(get("/api/solves").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %d)].penalty".formatted(solveId), hasSize(1)));
        mockMvc.perform(get("/api/stats").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dnfCount", is(1)))
                .andExpect(jsonPath("$.bestMs", is(1250)))
                .andExpect(jsonPath("$.averageMs", is(1750)));

        var maxTimeSolve = createSolve("attempt-" + UUID.randomUUID(), Integer.MAX_VALUE, false);
        mockMvc.perform(patch("/api/solves/{solveId}/penalty", jsonLong(maxTimeSolve, "id"))
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"penalty\":\"+2\"}"))
                .andExpect(status().isBadRequest());
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

    private MvcResult createSolve(String attemptId, String scramble, String penalty, int timerMs) throws Exception {
        var dnf = penalty.equals("dnf");
        var official = dnf ? "null" : Integer.toString(timerMs + (penalty.equals("+2") ? 2_000 : 0));
        return mockMvc.perform(post("/api/solves")
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientAttemptId":"%s","scramble":"%s","crossFaceRequested":"U",
                                 "timerMs":%d,"penalty":"%s","officialMs":%s,"dnf":%s}
                                """.formatted(attemptId, escapeJson(scramble), timerMs, penalty, official, dnf)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
                 "f2lSetupCaseCount":0,"f2lInsertCaseCount":0,"solvedF2LSlots":"FR,FL,BL,BR",
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
        return new tools.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get(field).longValue();
    }

    private static String jsonString(MvcResult result, String field) throws Exception {
        return new tools.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString()).get(field).textValue();
    }
}
