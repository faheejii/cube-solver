package server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiResponsesTest {
    @Test
    void errorResponse_shouldExposeStableCodeAndMatchingRequestId() throws Exception {
        var exchange = new TestExchange();

        ApiResponses.writeError(exchange, ApiErrorCode.FORBIDDEN, "Not permitted");

        JsonNode body = new ObjectMapper().readTree(exchange.output.toString());
        assertEquals(403, exchange.status);
        assertEquals("Not permitted", body.get("error").textValue());
        assertEquals("FORBIDDEN", body.get("code").textValue());
        assertEquals(exchange.headers.getFirst("X-Request-Id"), body.get("requestId").textValue());
        assertNotNull(exchange.headers.getFirst("Content-Type"));
    }

    private static final class TestExchange extends HttpExchange {
        private final Headers headers = new Headers();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int status;

        @Override public Headers getRequestHeaders() { return new Headers(); }
        @Override public Headers getResponseHeaders() { return headers; }
        @Override public URI getRequestURI() { return URI.create("/api/test"); }
        @Override public String getRequestMethod() { return "GET"; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(new byte[0]); }
        @Override public OutputStream getResponseBody() { return output; }
        @Override public void sendResponseHeaders(int status, long length) { this.status = status; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 1); }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 1); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream i, OutputStream o) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
