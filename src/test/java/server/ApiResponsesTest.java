package server;

import cfop.F2LCaseSignature;
import algorithms.AlgorithmCaseCatalog;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Corner;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.CubeStateSnapshot;
import cube.Edge;
import cube.Move;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;
import solver.CfopSolveResult;
import solver.CfopStageResult;
import solver.F2LCaseDescription;
import solver.F2LModeComparison;
import solver.F2LModeSummary;
import solver.F2LPairStep;
import solver.F2LReasonCode;
import solver.F2LSelectionEvidence;
import solver.F2LSolveTrace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void authUser_shouldExposeRole() throws Exception {
        var json = new ObjectMapper().readTree(
                JsonSupport.authUserJson(new database.AuthUser(1, "external", "admin@example.com", "Admin", "admin"))
        );

        assertEquals("admin", json.get("role").textValue());
    }

    @Test
    void solveResult_shouldExposeF2LTraceAndComparisonAdditively() throws Exception {
        var stage = new CfopStageResult("f2l", "U R", 2, true, "ok");
        var trace = new F2LSolveTrace(
                List.of(Move.U, Move.R),
                List.of(pairStep()),
                true
        );
        var summary = new F2LModeSummary("D", 2, 1, 1, 4, 0, List.of(F2LSlot.FR), false);
        var comparison = F2LModeComparison.between(summary, summary);
        var result = new CfopSolveResult(
                "R U", "D", "optimized", 1, 1,
                stage, stage, stage, stage, "[FR]", true, 1.25,
                comparison, trace
        );

        var json = new ObjectMapper().readTree(JsonSupport.solveResultJson(result));
        var f2l = json.get("f2l");

        assertEquals("U R", f2l.get("algorithm").textValue());
        assertEquals(2, f2l.get("moveCount").intValue());
        assertTrue(f2l.get("traceComplete").booleanValue() == false);
        assertTrue(f2l.get("pairAlgorithmMatchesStage").booleanValue());
        assertEquals(1, f2l.get("pairs").size());
        assertEquals(0, f2l.get("pairs").get(0).get("startMoveIndex").intValue());
        assertEquals(1, f2l.get("pairs").get(0).get("endMoveIndex").intValue());
        assertEquals("SHORTEST_AVAILABLE_PAIR",
                f2l.get("pairs").get(0).get("selectionEvidence").get("reasonCodes").get(0).textValue());
        assertEquals("FR", f2l.get("pairs").get(0).get("preservedSlots").get(0).textValue());
        assertNotNull(f2l.get("pairs").get(0).get("stateBefore"));
        assertNotNull(f2l.get("pairs").get(0).get("orientationAfter"));
        assertEquals("NO_MEASURABLE_IMPROVEMENT",
                json.get("comparison").get("explanationCodes").get(0).textValue());
    }

    @Test
    void solveResult_withEmptyCurrentTrace_shouldExposeEmptyTraceAndNullComparison() throws Exception {
        var stage = new CfopStageResult("stage", "", 0, true, "ok");
        var result = new CfopSolveResult(
                "", "U", "greedy", 0, 0,
                stage, stage, stage, stage, "[]", true, 0.0,
                null, new F2LSolveTrace(List.of(), List.of(), true)
        );

        var json = new ObjectMapper().readTree(JsonSupport.solveResultJson(result));

        assertEquals(false, json.get("f2l").get("traceComplete").booleanValue());
        assertEquals(0, json.get("f2l").get("pairs").size());
        assertTrue(json.get("comparison").isNull());
    }

    @Test
    void algorithmCatalog_shouldExposeAllCanonicalCfopPhases() throws Exception {
        var json = new ObjectMapper().readTree(
                JsonSupport.algorithmCatalogJson(AlgorithmCaseCatalog.entries(false), AlgorithmCaseCatalog.VERSION)
        );

        assertTrue(json.get("items").size() >= 194);
        assertTrue(java.util.stream.StreamSupport.stream(json.get("items").spliterator(), false)
                .anyMatch(item -> "oll".equals(item.get("phase").textValue())
                        && item.get("previewSetup").isTextual()
                        && "oll".equals(item.get("signature").get("kind").textValue())));
        assertTrue(java.util.stream.StreamSupport.stream(json.get("items").spliterator(), false)
                .anyMatch(item -> "pll".equals(item.get("phase").textValue())
                        && "pll".equals(item.get("signature").get("kind").textValue())));
    }

    private static F2LPairStep pairStep() {
        var snapshot = CubeStateSnapshot.from(new CubeState());
        return new F2LPairStep(
                1, Corner.DFR, Edge.FR, F2LSlot.FR,
                List.of(Move.U), List.of(Move.R), List.of(), List.of(Move.U, Move.R), true,
                snapshot, CubeOrientationKey.from(new cube.CubeOrientation()),
                snapshot, CubeOrientationKey.from(new cube.CubeOrientation()),
                F2LPreservationMask.of(List.of(F2LSlot.FR)),
                new F2LCaseDescription(new F2LCaseSignature(Corner.URF, 0, Edge.UF, 0), false, false, false),
                new F2LSelectionEvidence(2, 0, 2, 0, true, false, true, false,
                        List.of(F2LReasonCode.SHORTEST_AVAILABLE_PAIR))
        );
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
