package solver;

import cfop.F2LSlot;
import cube.Algorithm;
import cube.Move;

import java.util.List;

public record F2LModeSummary(
        String crossFace,
        int f2lMoves,
        int ollMoves,
        int pllMoves,
        int totalMoves,
        int rotationCount,
        List<F2LSlot> pairOrder,
        boolean pairTraceComplete
) {
    public static F2LModeSummary from(
            String crossFace,
            F2LSolveTrace f2lTrace,
            CfopStageResult oll,
            CfopStageResult pll
    ) {
        if (f2lTrace == null || oll == null || pll == null) {
            throw new IllegalArgumentException("mode summary inputs cannot be null");
        }
        var f2lMoves = f2lTrace.algorithm().getMoveCount();
        var ollMoves = oll.moveCount();
        var pllMoves = pll.moveCount();
        var pairOrder = f2lTrace.pairSteps().stream()
                .map(step -> step.targetSlot())
                .toList();
        var rotationCount = countRotations(f2lTrace.algorithm())
                + countRotations(oll.algorithm())
                + countRotations(pll.algorithm());
        return new F2LModeSummary(
                crossFace,
                f2lMoves,
                ollMoves,
                pllMoves,
                f2lMoves + ollMoves + pllMoves,
                rotationCount,
                pairOrder,
                f2lTrace.hasCompletePairTrace()
        );
    }

    public F2LModeSummary {
        if (crossFace == null || crossFace.isBlank()) {
            throw new IllegalArgumentException("crossFace cannot be null or blank");
        }
        if (f2lMoves < 0 || ollMoves < 0 || pllMoves < 0 || totalMoves < 0 || rotationCount < 0) {
            throw new IllegalArgumentException("mode summary metrics cannot be negative");
        }
        if (totalMoves != f2lMoves + ollMoves + pllMoves) {
            throw new IllegalArgumentException("totalMoves must equal the stage move sum");
        }
        pairOrder = pairOrder == null ? List.of() : List.copyOf(pairOrder);
        if (pairOrder.stream().anyMatch(slot -> slot == null)) {
            throw new IllegalArgumentException("pairOrder cannot contain null slots");
        }
    }

    private static int countRotations(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return 0;
        }
        return (int) Algorithm.parse(algorithm).getMoves().stream()
                .filter(Move::isCubeRotation)
                .count();
    }

    private static int countRotations(Algorithm algorithm) {
        return (int) algorithm.getMoves().stream()
                .filter(Move::isCubeRotation)
                .count();
    }
}
