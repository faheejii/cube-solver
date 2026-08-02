package solver;

import java.util.ArrayList;
import java.util.List;

public record F2LModeComparison(
        F2LModeSummary fast,
        F2LModeSummary optimized,
        int f2lMoveDifference,
        int ollMoveDifference,
        int pllMoveDifference,
        int totalMoveDifference,
        int rotationDifference,
        boolean pairOrderChanged,
        List<F2LComparisonCode> explanationCodes
) {
    public F2LModeComparison {
        if (fast == null || optimized == null) {
            throw new IllegalArgumentException("comparison summaries cannot be null");
        }
        explanationCodes = explanationCodes == null ? List.of() : List.copyOf(explanationCodes);
    }

    public static F2LModeComparison between(F2LModeSummary fast, F2LModeSummary optimized) {
        if (!fast.crossFace().equals(optimized.crossFace())) {
            throw new IllegalArgumentException("mode comparison requires the same cross face");
        }

        var f2lDifference = optimized.f2lMoves() - fast.f2lMoves();
        var ollDifference = optimized.ollMoves() - fast.ollMoves();
        var pllDifference = optimized.pllMoves() - fast.pllMoves();
        var totalDifference = optimized.totalMoves() - fast.totalMoves();
        var rotationDifference = optimized.rotationCount() - fast.rotationCount();
        var pairOrderChanged = fast.pairTraceComplete()
                && optimized.pairTraceComplete()
                && !fast.pairOrder().equals(optimized.pairOrder());

        var codes = new ArrayList<F2LComparisonCode>();
        if (f2lDifference < 0) {
            codes.add(F2LComparisonCode.SHORTER_F2L);
        }
        if (ollDifference + pllDifference < 0) {
            codes.add(F2LComparisonCode.SHORTER_LAST_LAYER);
        }
        if (totalDifference < 0) {
            codes.add(F2LComparisonCode.SHORTER_TOTAL_ROUTE);
        }
        if (rotationDifference < 0) {
            codes.add(F2LComparisonCode.FEWER_ROTATIONS);
        }
        if (pairOrderChanged) {
            codes.add(F2LComparisonCode.DIFFERENT_PAIR_ORDER);
        }
        if (f2lDifference > 0 && totalDifference < 0) {
            codes.add(F2LComparisonCode.LOCAL_PAIR_LONGER_GLOBAL_ROUTE_SHORTER);
        }
        if (f2lDifference == 0
                && ollDifference == 0
                && pllDifference == 0
                && rotationDifference == 0
                && !pairOrderChanged) {
            codes.add(F2LComparisonCode.NO_MEASURABLE_IMPROVEMENT);
        }

        return new F2LModeComparison(
                fast,
                optimized,
                f2lDifference,
                ollDifference,
                pllDifference,
                totalDifference,
                rotationDifference,
                pairOrderChanged,
                codes
        );
    }
}
