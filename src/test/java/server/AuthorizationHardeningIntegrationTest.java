package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import solver.CfopSolveService;
import test.PostgresTestDatabase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationHardeningIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void algorithmCatalog_shouldRequireAdminAndRegistrationRoleBodyCannotEscalate() throws Exception {
        var previousAdminEmail = System.getProperty("admin.email");
        var adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        var userEmail = "user-" + UUID.randomUUID() + "@example.com";
        System.setProperty("admin.email", adminEmail);

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
                var anonymous = new TestClient();
                var user = new TestClient();
                var admin = new TestClient();

                assertEquals(200, get(anonymous, baseUri.resolve("/api/health/live")).statusCode());
                assertEquals(200, get(anonymous, baseUri.resolve("/api/health/ready")).statusCode());
                assertEquals(404, get(anonymous, baseUri.resolve("/api/health")).statusCode());
                assertError(get(anonymous, baseUri.resolve("/api/unknown")), 404, "Not found");

                assertError(get(anonymous, baseUri.resolve("/api/algorithms")), 401, "Authentication required");

                var userRegistration = register(user, baseUri, userEmail, "admin");
                assertEquals(201, userRegistration.statusCode(), userRegistration.body());
                assertEquals("user", JSON.readTree(get(user, baseUri.resolve("/api/auth/me")).body())
                        .get("role").textValue());
                assertError(get(user, baseUri.resolve("/api/algorithms")), 403, "Administrator access required");

                var adminRegistration = register(admin, baseUri, adminEmail, null);
                assertEquals(201, adminRegistration.statusCode(), adminRegistration.body());
                assertEquals("admin", JSON.readTree(get(admin, baseUri.resolve("/api/auth/me")).body())
                        .get("role").textValue());

                var catalog = get(admin, baseUri.resolve("/api/algorithms"));
                assertEquals(200, catalog.statusCode(), catalog.body());
                assertTrue(JSON.readTree(catalog.body()).get("items").size() > 0);

                assertEquals(404, get(admin, baseUri.resolve("/api/algorithms/f2l")).statusCode());
            } finally {
                server.stop(0);
                cubeServer.close();
            }
        } finally {
            restoreProperty("admin.email", previousAdminEmail);
        }
    }

    private static HttpResponse<String> register(
            TestClient client,
            URI baseUri,
            String email,
            String role
    ) throws Exception {
        var roleField = role == null ? "" : ",\"role\":\"" + role + "\"";
        return postJson(client, baseUri.resolve("/api/auth/register"), """
                {"email":"%s","password":"integration-password","displayName":"Integration"%s}
                """.formatted(email, roleField));
    }

    private static HttpResponse<String> get(TestClient client, URI uri) throws Exception {
        return send(client, request(client, uri).GET().build());
    }

    private static HttpResponse<String> postJson(TestClient client, URI uri, String body) throws Exception {
        return send(client,
                request(client, uri)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build()
        );
    }

    private static HttpResponse<String> send(TestClient client, HttpRequest request) throws Exception {
        var response = client.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        response.headers().firstValue("Set-Cookie").ifPresent(client::captureCookie);
        return response;
    }

    private static HttpRequest.Builder request(TestClient client, URI uri) {
        var builder = HttpRequest.newBuilder(uri);
        if (client.sessionToken != null) {
            builder.header("Cookie", SessionCookie.NAME + "=" + client.sessionToken);
        }
        return builder;
    }

    private static void assertError(HttpResponse<String> response, int status, String message) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.body().toLowerCase().contains(message.toLowerCase()), response.body());
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static final class TestClient {
        private final HttpClient httpClient = HttpClient.newHttpClient();
        private String sessionToken;

        private void captureCookie(String setCookie) {
            var prefix = SessionCookie.NAME + "=";
            var start = setCookie.indexOf(prefix);
            if (start < 0) {
                return;
            }
            var valueStart = start + prefix.length();
            var valueEnd = setCookie.indexOf(';', valueStart);
            var value = setCookie.substring(valueStart, valueEnd < 0 ? setCookie.length() : valueEnd);
            sessionToken = value.isEmpty() ? null : value;
        }
    }
}
