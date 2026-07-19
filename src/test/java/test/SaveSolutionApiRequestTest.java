package test;

import api.SaveSolutionApiRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SaveSolutionApiRequestTest {
    @Test
    void constructor_shouldAcceptCompleteSolution() {
        assertDoesNotThrow(() -> validRequest("optimized"));
    }

    @Test
    void constructor_shouldRequireMode() {
        assertThrows(IllegalArgumentException.class, () -> validRequest(""));
    }

    @Test
    void constructor_shouldRejectNegativeMetrics() {
        assertThrows(IllegalArgumentException.class, () -> validRequestWithTotalMoves(-1));
    }

    private static SaveSolutionApiRequest validRequest(String mode) {
        return validRequestWithTotalMoves(52, mode);
    }

    private static SaveSolutionApiRequest validRequestWithTotalMoves(int totalMoves) {
        return validRequestWithTotalMoves(totalMoves, "optimized");
    }

    private static SaveSolutionApiRequest validRequestWithTotalMoves(int totalMoves, String mode) {
        return new SaveSolutionApiRequest(
                "user-1",
                "CN",
                "U",
                mode,
                121,
                14,
                "[FR, FL, BL, BR]",
                totalMoves,
                true,
                42.5,
                "z2 R",
                1,
                true,
                "ok",
                "R U R'",
                3,
                true,
                "ok",
                "R U R'",
                3,
                true,
                "ok",
                "U",
                1,
                true,
                "ok"
        );
    }
}
