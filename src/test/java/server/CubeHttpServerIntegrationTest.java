package server;

import database.DatabaseConfig;
import database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import test.PostgresTestDatabase;
import solver.CfopSolveService;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CubeHttpServerIntegrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void authenticatedEndpoints_shouldEnforceSessionsOwnershipAndErrorContract() throws Exception {
        try (var postgres = PostgresTestDatabase.create()) {
            var database = postgres.manager();
            database.initialize();
            var cubeServer = new CubeHttpServer(
                    new CfopSolveService(),
                    Path.of("missing-frontend"),
                    database,
                    false
            );
            var server = cubeServer.create(0);
            server.start();

            try {
                var baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
                var alice = client();
                var bob = client();
                var aliceEmail = "alice-" + UUID.randomUUID() + "@example.com";
                var bobEmail = "bob-" + UUID.randomUUID() + "@example.com";

                var unauthenticatedHistory = get(alice, baseUri.resolve("/api/solves"));
                assertError(unauthenticatedHistory, 401, "Authentication required");
                assertEquals("private, no-store", unauthenticatedHistory.headers()
                        .firstValue("Cache-Control").orElseThrow());
                assertTrue(unauthenticatedHistory.headers().firstValue("X-Request-Id").isPresent());

                var aliceRegistration = register(alice, baseUri, aliceEmail);
                assertEquals(201, aliceRegistration.statusCode());
                assertTrue(aliceRegistration.body().contains(aliceEmail));
                assertTrue(cookie(alice) != null);

                var duplicateRegistration = register(alice, baseUri, aliceEmail);
                assertError(duplicateRegistration, 409, "already exists");

                var badLogin = postJson(bob, baseUri.resolve("/api/auth/login"), """
                        {"email":"%s","password":"wrong-password"}
                        """.formatted(aliceEmail));
                assertError(badLogin, 401, "Invalid email or password");

                var bobRegistration = register(bob, baseUri, bobEmail);
                assertEquals(201, bobRegistration.statusCode());

                var attempt = postJson(alice, baseUri.resolve("/api/solves"), """
                        {"userId":"forged-user","clientAttemptId":"attempt-%s","scramble":"R U",
                         "crossFaceRequested":"F","timerMs":1234,"penalty":"none","officialMs":1234,"dnf":false}
                        """.formatted(UUID.randomUUID()));
                assertEquals(201, attempt.statusCode());
                var solveId = jsonLong(attempt.body(), "id");
                var clientAttemptId = jsonString(attempt.body(), "clientAttemptId");

                var aliceHistory = get(alice, baseUri.resolve("/api/solves"));
                assertEquals(200, aliceHistory.statusCode());
                assertTrue(aliceHistory.body().contains(clientAttemptId));
                var bobHistory = get(bob, baseUri.resolve("/api/solves"));
                assertEquals(200, bobHistory.statusCode());
                assertFalse(bobHistory.body().contains(clientAttemptId));

                var bobDetail = get(bob, baseUri.resolve("/api/solves/" + solveId));
                assertError(bobDetail, 400, "Solve not found");
                var bobDelete = delete(bob, baseUri.resolve("/api/solves/" + solveId));
                assertError(bobDelete, 400, "Solve not found");

                var aliceJobResponse = postJson(alice, baseUri.resolve("/api/solve-jobs"), """
                        {"scramble":"R","crossFace":"F","f2lMode":"greedy","userId":"forged-user",
                         "saveOnComplete":false}
                        """);
                assertEquals(202, aliceJobResponse.statusCode());
                var jobId = jsonString(aliceJobResponse.body(), "id");

                var anonymousJob = get(client(), baseUri.resolve("/api/solve-jobs/" + jobId));
                assertError(anonymousJob, 401, "Authentication required");
                var bobJob = get(bob, baseUri.resolve("/api/solve-jobs/" + jobId));
                assertError(bobJob, 403, "not allowed");
                var bobCancel = delete(bob, baseUri.resolve("/api/solve-jobs/" + jobId));
                assertError(bobCancel, 403, "not allowed");
                var aliceJob = get(alice, baseUri.resolve("/api/solve-jobs/" + jobId));
                assertEquals(200, aliceJob.statusCode());
                assertEquals(200, delete(alice, baseUri.resolve("/api/solve-jobs/" + jobId)).statusCode());

                expireSession(database, cookie(alice).getValue());
                var expiredMe = get(alice, baseUri.resolve("/api/auth/me"));
                assertError(expiredMe, 401, "Authentication required");

                var login = postJson(alice, baseUri.resolve("/api/auth/login"), """
                        {"email":"%s","password":"integration-password"}
                        """.formatted(aliceEmail));
                assertEquals(200, login.statusCode());
                assertTrue(get(alice, baseUri.resolve("/api/auth/me")).body().contains(aliceEmail));

                var logout = postJson(alice, baseUri.resolve("/api/auth/logout"), "{}");
                assertEquals(204, logout.statusCode());
                assertError(get(alice, baseUri.resolve("/api/auth/me")), 401, "Authentication required");
            } finally {
                server.stop(0);
                cubeServer.close();
            }
        }
    }

    private static CookieManager cookies() {
        return new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(cookies()).build();
    }

    private static HttpResponse<String> register(HttpClient client, URI baseUri, String email) throws Exception {
        return postJson(client, baseUri.resolve("/api/auth/register"), """
                {"email":"%s","password":"integration-password","displayName":"Integration"}
                """.formatted(email));
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> delete(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).DELETE().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> postJson(HttpClient client, URI uri, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static void expireSession(DatabaseManager database, String token) throws SQLException {
        try (var connection = database.openConnection(); var statement = connection.prepareStatement("""
                UPDATE auth_sessions SET expires_at = NOW() - INTERVAL '1 second'
                WHERE token_hash = ?
                """)) {
            statement.setString(1, SessionToken.hash(token));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static HttpCookie cookie(HttpClient client) {
        return ((CookieManager) client.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream()
                .filter(value -> SessionCookie.NAME.equals(value.getName()))
                .findFirst()
                .orElse(null);
    }

    private static void assertError(HttpResponse<String> response, int status, String message) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"error\""), response.body());
        assertTrue(response.body().toLowerCase().contains(message.toLowerCase()), response.body());
    }

    private static String jsonString(String json, String field) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get(field).asText();
    }

    private static long jsonLong(String json, String field) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get(field).asLong();
    }
}
