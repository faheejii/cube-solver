package server;

import com.fasterxml.jackson.databind.ObjectMapper;
import database.SavedSolution;
import database.SolveHistoryDetail;
import org.junit.jupiter.api.Test;
import solver.CfopStageResult;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SolveHistoryResponseTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void historyDetail_shouldPreservePlaybackInputsFromScrambleCrossF2LAndTrace() throws Exception {
        var trace = "{\"traceComplete\":true,\"pairs\":[{\"order\":1,\"algorithm\":\"R U\"}]}";
        var detail = new SolveHistoryDetail(
                41, "attempt-41", "R U2 F'", "D", 1500, 1500, "none", false,
                OffsetDateTime.now(), List.of(new SavedSolution(
                        "greedy", "D", "D", 1, 2,
                        stage("cross", "D"), stage("f2l", "R U R'"), stage("oll", "F R U"), stage("pll", "R2"),
                        "[FR]", true, 10, 20.0, "cfop-web-v1", OffsetDateTime.now(), trace, null
                ))
        );

        var json = JSON.readTree(JsonSupport.solveHistoryDetailJson(detail));
        var solution = json.get("solutions").get(0);

        assertEquals("R U2 F'", json.get("scramble").textValue());
        assertEquals("D", solution.get("cross").get("algorithm").textValue());
        assertEquals("R U R'", solution.get("f2l").get("algorithm").textValue());
        assertEquals("R U", solution.get("f2l").get("pairs").get(0).get("algorithm").textValue());
    }

    private static CfopStageResult stage(String name, String algorithm) {
        return new CfopStageResult(name, algorithm, algorithm.isBlank() ? 0 : algorithm.split(" ").length, true, "solved");
    }
}
