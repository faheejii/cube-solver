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
}
