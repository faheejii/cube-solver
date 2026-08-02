package test;

import cfop.F2LSlot;
import org.junit.jupiter.api.Test;
import solver.F2LComparisonCode;
import solver.F2LModeComparison;
import solver.F2LModeSummary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LModeComparisonTest {
    @Test
    void between_shouldReportSignedDifferencesAndFactualReasons() {
        var fast = summary(20, 10, 13, 2, List.of(F2LSlot.FR, F2LSlot.FL, F2LSlot.BL, F2LSlot.BR));
        var optimized = summary(21, 8, 13, 1, List.of(F2LSlot.FL, F2LSlot.FR, F2LSlot.BL, F2LSlot.BR));

        var comparison = F2LModeComparison.between(fast, optimized);

        assertEquals(1, comparison.f2lMoveDifference());
        assertEquals(-2, comparison.ollMoveDifference());
        assertEquals(0, comparison.pllMoveDifference());
        assertEquals(-1, comparison.totalMoveDifference());
        assertEquals(-1, comparison.rotationDifference());
        assertTrue(comparison.pairOrderChanged());
        assertTrue(comparison.explanationCodes().contains(F2LComparisonCode.SHORTER_LAST_LAYER));
        assertTrue(comparison.explanationCodes().contains(F2LComparisonCode.SHORTER_TOTAL_ROUTE));
        assertTrue(comparison.explanationCodes().contains(F2LComparisonCode.FEWER_ROTATIONS));
        assertTrue(comparison.explanationCodes().contains(F2LComparisonCode.DIFFERENT_PAIR_ORDER));
        assertTrue(comparison.explanationCodes().contains(
                F2LComparisonCode.LOCAL_PAIR_LONGER_GLOBAL_ROUTE_SHORTER
        ));
    }

    @Test
    void between_shouldReportNoMeasurableImprovementWhenRoutesMatch() {
        var summary = summary(20, 10, 13, 2, List.of(F2LSlot.FR, F2LSlot.FL, F2LSlot.BL, F2LSlot.BR));

        var comparison = F2LModeComparison.between(summary, summary);

        assertEquals(List.of(F2LComparisonCode.NO_MEASURABLE_IMPROVEMENT), comparison.explanationCodes());
        assertTrue(!comparison.pairOrderChanged());
    }

    @Test
    void between_shouldRejectDifferentCrossFaces() {
        var fast = summary(20, 10, 13, 2, List.of());
        var optimized = new F2LModeSummary("U", 20, 10, 13, 43, 2, List.of(), false);

        assertThrows(IllegalArgumentException.class, () -> F2LModeComparison.between(fast, optimized));
    }

    private static F2LModeSummary summary(
            int f2lMoves,
            int ollMoves,
            int pllMoves,
            int rotations,
            List<F2LSlot> pairOrder
    ) {
        return new F2LModeSummary(
                "D",
                f2lMoves,
                ollMoves,
                pllMoves,
                f2lMoves + ollMoves + pllMoves,
                rotations,
                pairOrder,
                pairOrder.size() == 4
        );
    }
}
