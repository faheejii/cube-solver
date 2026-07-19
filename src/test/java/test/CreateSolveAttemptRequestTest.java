package test;

import api.CreateSolveAttemptRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CreateSolveAttemptRequestTest {
    @Test
    void constructor_shouldAcceptValidAttempt() {
        assertDoesNotThrow(() -> new CreateSolveAttemptRequest(
                "user-1",
                "attempt-1",
                "R U R'",
                "CN",
                12_340,
                "none",
                12_340,
                false
        ));
    }

    @Test
    void constructor_shouldRequireClientAttemptId() {
        assertThrows(IllegalArgumentException.class, () -> new CreateSolveAttemptRequest(
                "user-1",
                "",
                "R U R'",
                "U",
                12_340,
                "none",
                12_340,
                false
        ));
    }

    @Test
    void constructor_shouldRejectInconsistentDnfAndPenalty() {
        assertThrows(IllegalArgumentException.class, () -> new CreateSolveAttemptRequest(
                "user-1", "attempt-2", "R", "U", 1_000, "dnf", null, false
        ));
    }

    @Test
    void constructor_shouldRejectNegativeTimes() {
        assertThrows(IllegalArgumentException.class, () -> new CreateSolveAttemptRequest(
                "user-1", "attempt-3", "R", "U", -1, "none", 1, false
        ));
    }
}
