package server.web;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import server.SpringCubeApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import database.algorithms.AlgorithmCatalogSeedImportService;
import database.algorithms.AlgorithmCatalogStateJpaRepository;
import database.algorithms.UserDisabledAlgorithmJpaRepository;
import database.algorithms.UserEnabledTrialRevisionJpaRepository;
import test.support.PostgresTestDatabase;
import database.persistence.repository.AuthSessionJpaRepository;
import database.persistence.repository.UserJpaRepository;

import jakarta.servlet.http.Cookie;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                "server.cors.origin=http://localhost:5173",
                "admin.email=solver-profile-admin@example.test"
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
    private static final AtomicInteger AUTH_CLIENT_SEQUENCE = new AtomicInteger();
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
        registry.add("server.auth-requests-per-minute", () -> 1000);
        registry.add("server.auth-requests-per-minute", () -> 1000);
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
        assertNotNull(applicationContext.getBean(UserDisabledAlgorithmJpaRepository.class));
        assertNotNull(applicationContext.getBean(UserEnabledTrialRevisionJpaRepository.class));
    }

    @Test
    void algorithmSeed_shouldImportAllSourceDefinitionsOnlyOnce() {
        var states = applicationContext.getBean(AlgorithmCatalogStateJpaRepository.class);

        var jdbc = applicationContext.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        assertEquals(201, jdbc.queryForObject("SELECT COUNT(*) FROM algorithm_catalog_algorithms WHERE seed_key IS NOT NULL", Integer.class));
        assertEquals(201, jdbc.queryForObject("SELECT COUNT(*) FROM algorithm_catalog_revisions r JOIN algorithm_catalog_algorithms a ON a.id = r.algorithm_id WHERE a.seed_key IS NOT NULL", Integer.class));
        assertEquals(201, jdbc.queryForObject("SELECT COUNT(*) FROM algorithm_catalog_workflow_events WHERE action = 'seed-import-approved'", Integer.class));
        var state = states.findById((short) 1).orElseThrow();
        assertNotNull(state.getInitializedAt());
        assertEquals("1", state.getSeedVersion());
        assertEquals(201, jdbc
                .queryForObject("SELECT COUNT(*) FROM algorithm_catalog_algorithms WHERE seed_key IS NOT NULL AND seed_order BETWEEN 0 AND 200",
                        Integer.class));

        var repeat = applicationContext.getBean(AlgorithmCatalogSeedImportService.class).initializeSeedCatalog();
        assertFalse(repeat.initializedNow());
        assertEquals(201, jdbc.queryForObject("SELECT COUNT(*) FROM algorithm_catalog_algorithms WHERE seed_key IS NOT NULL", Integer.class));
        assertEquals(201, jdbc.queryForObject("SELECT COUNT(*) FROM algorithm_catalog_revisions r JOIN algorithm_catalog_algorithms a ON a.id = r.algorithm_id WHERE a.seed_key IS NOT NULL", Integer.class));
        assertEquals(201, jdbc.queryForObject("SELECT COUNT(*) FROM algorithm_catalog_workflow_events WHERE action = 'seed-import-approved'", Integer.class));
    }

    @Test
    void algorithmCatalog_shouldReadSeedFromDatabaseAndUseStableCursorPages() throws Exception {
        var catalogAdminEmail = uniqueEmail("catalog-admin");
        loginAs(catalogAdminEmail);
        var catalogAdmin = applicationContext.getBean(UserJpaRepository.class)
                .findCredentialByEmail(catalogAdminEmail).orElseThrow();
        catalogAdmin.setRole("admin");
        applicationContext.getBean(UserJpaRepository.class).saveAndFlush(catalogAdmin);
        var first = mockMvc.perform(get("/api/algorithms").cookie(sessionCookie()).param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].algorithmId", notNullValue()))
                .andExpect(jsonPath("$.items[0].revisionId", notNullValue()))
                .andExpect(jsonPath("$.items[0].status", is("canonical")))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andReturn();
        var cursor = jsonString(first, "nextCursor");
        var firstAlgorithmId = new tools.jackson.databind.ObjectMapper()
                .readTree(first.getResponse().getContentAsString()).path("items").get(0).path("algorithmId").asText();
        mockMvc.perform(get("/api/algorithms").cookie(sessionCookie()).param("limit", "20").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].algorithmId", notNullValue()))
                .andExpect(jsonPath("$.items[0].algorithmId", org.hamcrest.Matchers.not(is(firstAlgorithmId))))
                .andExpect(jsonPath("$.catalogVersion").doesNotExist());
    }

    @Test
    void algorithmGovernance_shouldRequireExplicitTrialAndAdminApproval() throws Exception {
        var adminEmail = uniqueEmail("catalog-workflow-admin");
        loginAs(adminEmail);
        var admin = applicationContext.getBean(UserJpaRepository.class).findCredentialByEmail(adminEmail).orElseThrow();
        admin.setRole("admin");
        applicationContext.getBean(UserJpaRepository.class).saveAndFlush(admin);

        var knownOll = algorithms.AlgorithmCaseCatalog.entries(false).stream()
                .filter(entry -> "oll".equals(entry.phase())).findFirst().orElseThrow();
        var definition = "{\"phase\":\"oll\",\"name\":\"workflow-oll-" + UUID.randomUUID()
                + "\",\"algorithm\":\"U U' " + knownOll.algorithm() + "\",\"notes\":\"workflow fixture\"}";
        var created = mockMvc.perform(post("/api/algorithms").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON).content(definition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("experimental")))
                .andExpect(jsonPath("$.revisionNumber", is(1)))
                .andReturn();
        var algorithmId = jsonString(created, "algorithmId");
        var revisionId = jsonString(created, "id");

        mockMvc.perform(get("/api/algorithms/{id}", algorithmId).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisions", hasSize(1)));
        mockMvc.perform(post("/api/algorithms/{id}/revisions/{revisionId}/actions", algorithmId, revisionId)
                        .cookie(sessionCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"deprecate\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/solver-profile").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabledTrialRevisionIds\":[\"" + revisionId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledTrialRevisionIds", hasSize(1)));

        mockMvc.perform(post("/api/algorithms/{id}/revisions/{revisionId}/actions", algorithmId, revisionId)
                        .cookie(sessionCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"submit\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("test-only")))
                .andReturn();
        mockMvc.perform(get("/api/admin/solver-profile").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledTrialRevisionIds", hasSize(1)));

        mockMvc.perform(post("/api/algorithms/{id}/revisions/{revisionId}/actions", algorithmId, revisionId)
                        .cookie(sessionCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"withdraw\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("experimental")));

        mockMvc.perform(post("/api/algorithms/{id}/revisions/{revisionId}/actions", algorithmId, revisionId)
                        .cookie(sessionCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"submit\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("test-only")))
                .andReturn();
        mockMvc.perform(post("/api/algorithms/{id}/revisions/{revisionId}/actions", algorithmId, revisionId)
                        .cookie(sessionCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"approve\",\"expectedVersion\":3,\"reason\":\"verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("canonical")))
                .andReturn();
        mockMvc.perform(post("/api/algorithms/{id}/revisions/{revisionId}/actions", algorithmId, revisionId)
                        .cookie(sessionCookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"deprecate\",\"expectedVersion\":4,\"reason\":\"retired\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("deprecated")));
        mockMvc.perform(get("/api/algorithms").cookie(sessionCookie())
                        .param("status", "deprecated").param("q", "workflow-oll-"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].revisionId", is(revisionId)));
    }

    @Test
    void userRoleManagement_shouldEnforceCapabilitiesAndAuditChanges() throws Exception {
        var targetEmail = uniqueEmail("role-target");
        loginAs(targetEmail);
        var targetSessionToken = sessionToken;
        mockMvc.perform(get("/api/admin/users").cookie(sessionCookie()))
                .andExpect(status().isForbidden());

        var adminEmail = uniqueEmail("role-admin");
        loginAs(adminEmail);
        var adminAccount = applicationContext.getBean(UserJpaRepository.class)
                .findCredentialByEmail(adminEmail).orElseThrow();
        adminAccount.setRole("admin");
        applicationContext.getBean(UserJpaRepository.class).saveAndFlush(adminAccount);
        var adminSessionToken = sessionToken;
        mockMvc.perform(get("/api/admin/users").cookie(sessionCookie()).param("q", "role-target").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].email", is(targetEmail)))
                .andExpect(jsonPath("$.items[0].role", is("user")))
                .andExpect(jsonPath("$.items[0].protected", is(false)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));

        var target = applicationContext.getBean(UserJpaRepository.class)
                .findCredentialByEmail(targetEmail).orElseThrow();
        var expectedUpdatedAt = target.getUpdatedAt().toString();
        var roleChange = mockMvc.perform(patch("/api/admin/users/{id}/role", target.getId())
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"maintainer\",\"expectedUpdatedAt\":\"" + expectedUpdatedAt + "\"}"))
                .andReturn();
        assertTrue(roleChange.getResponse().getStatus() == 200, roleChange.getResponse().getContentAsString());
        org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.role", is("maintainer"))
                .match(roleChange);
        assertTrue(applicationContext.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                .queryForObject("SELECT count(*) FROM user_role_changes WHERE target_user_id = ?", Integer.class,
                        target.getId()) == 1);

        sessionToken = targetSessionToken;
        mockMvc.perform(get("/api/admin/solver-profile").cookie(sessionCookie()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").cookie(sessionCookie()))
                .andExpect(status().isForbidden());

        sessionToken = adminSessionToken;
        mockMvc.perform(patch("/api/admin/users/{id}/role", target.getId())
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"user\",\"expectedUpdatedAt\":\"" + expectedUpdatedAt + "\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/auth/register")
                        .with(uniqueAuthClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("solver-profile-admin@example.test")))
                .andExpect(status().isCreated());
        var bootstrap = applicationContext.getBean(UserJpaRepository.class)
                .findCredentialByEmail("solver-profile-admin@example.test").orElseThrow();
        mockMvc.perform(patch("/api/admin/users/{id}/role", bootstrap.getId())
                        .cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"user\",\"expectedUpdatedAt\":\""
                                + bootstrap.getUpdatedAt() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void solverProfile_shouldBeAdminOnlyIsolatedAndCapturedBySolveJob() throws Exception {
        loginAs(uniqueEmail("profile-user"));
        mockMvc.perform(get("/api/admin/solver-profile").cookie(sessionCookie()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/solver-profile").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disabledAlgorithms\":[\"pll:case-1\"]}"))
                .andExpect(status().isForbidden());

        var profileAdminEmail = uniqueEmail("profile-admin");
        loginAs(profileAdminEmail);
        var profileAdmin = applicationContext.getBean(UserJpaRepository.class)
                .findCredentialByEmail(profileAdminEmail).orElseThrow();
        profileAdmin.setRole("admin");
        applicationContext.getBean(UserJpaRepository.class).saveAndFlush(profileAdmin);
        mockMvc.perform(get("/api/admin/solver-profile").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabledAlgorithms", hasSize(0)));
        mockMvc.perform(put("/api/admin/solver-profile").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disabledAlgorithms\":[\"pll:case-1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabledAlgorithms", contains("pll:case-1")));
        mockMvc.perform(get("/api/admin/solver-profile").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabledAlgorithms", contains("pll:case-1")));

        var secondAdminEmail = uniqueEmail("profile-admin-other");
        loginAs(secondAdminEmail);
        var users = applicationContext.getBean(UserJpaRepository.class);
        var secondAdmin = users.findCredentialByEmail(secondAdminEmail).orElseThrow();
        secondAdmin.setRole("admin");
        users.saveAndFlush(secondAdmin);
        mockMvc.perform(get("/api/admin/solver-profile").cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabledAlgorithms", hasSize(0)));
        mockMvc.perform(put("/api/admin/solver-profile").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"disabledAlgorithms\":[\"pll:case-2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabledAlgorithms", contains("pll:case-2")));

        mockMvc.perform(post("/api/solve-jobs").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scramble\":\"R\",\"crossFace\":\"U\",\"f2lMode\":\"greedy\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.disabledAlgorithms", contains("pll:case-2")));

        var solve = createSolve("attempt-" + UUID.randomUUID(), "U", "none", 900);
        var solveId = jsonLong(solve, "id");
        var persistedJob = mockMvc.perform(post("/api/solve-jobs").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scramble\":\"U\",\"crossFace\":\"U\",\"f2lMode\":\"greedy\","
                                + "\"solveId\":" + solveId + ",\"saveOnComplete\":true}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.disabledAlgorithms", contains("pll:case-2")))
                .andReturn();
        var persistedJobId = jsonString(persistedJob, "id");
        MvcResult latest = null;
        String state = null;
        for (int attempt = 0; attempt < 120; attempt++) {
            latest = mockMvc.perform(get("/api/solve-jobs/" + persistedJobId).cookie(sessionCookie())).andReturn();
            state = jsonString(latest, "status");
            if ("completed".equals(state) || "failed".equals(state) || "timed_out".equals(state)) break;
            Thread.sleep(250);
        }
        assertTrue("completed".equals(state), latest == null ? "No solve-job response" : latest.getResponse().getContentAsString());
        mockMvc.perform(get("/api/solves/" + solveId).cookie(sessionCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solutions[0].disabledAlgorithms", contains("pll:case-2")));

        var regularEmail = uniqueEmail("profile-isolation");
        loginAs(regularEmail);
        mockMvc.perform(post("/api/solve-jobs").cookie(sessionCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scramble\":\"R\",\"crossFace\":\"U\",\"f2lMode\":\"greedy\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.disabledAlgorithms", hasSize(0)));
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
                        .with(uniqueAuthClient())
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
                        .with(uniqueAuthClient())
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
                .andExpect(jsonPath("$.solutions[0].mode", is("greedy")))
                .andExpect(jsonPath("$.solutions[0].disabledAlgorithms", hasSize(0)));
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
        var registration = mockMvc.perform(post("/api/auth/register")
                        .with(uniqueAuthClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andReturn();
        if (registration.getResponse().getStatus() == 201) {
            captureSession(registration);
            return;
        }
        var login = mockMvc.perform(post("/api/auth/login")
                        .with(uniqueAuthClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isOk())
                .andReturn();
        captureSession(login);
    }

    private static RequestPostProcessor uniqueAuthClient() {
        var address = "192.0.2." + (AUTH_CLIENT_SEQUENCE.incrementAndGet() % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
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
