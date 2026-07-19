package test;

import org.junit.jupiter.api.Test;
import solver.SolveCancellation;
import solver.SolveDeadlineExceededException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SolveCancellationTest {
    @Test
    void deadline_shouldInterruptExpensiveLoop() {
        assertThrows(SolveDeadlineExceededException.class, () -> SolveCancellation.withDeadline(
                1_000_000,
                () -> {
                    while (true) {
                        SolveCancellation.throwIfCancelled();
                    }
                }
        ));
    }
}
