package solver;

import java.util.List;

public record F2LSelectionEvidence(
        int pairMoveCount,
        int remainingF2LMoveCount,
        int totalRouteMoveCount,
        int rotationCount,
        boolean preservesSolvedSlots,
        boolean pairWasAlreadyConnected,
        boolean shortestAvailablePair,
        boolean selectedForGlobalRoute,
        List<F2LReasonCode> reasonCodes
) {
    public F2LSelectionEvidence {
        if (pairMoveCount < 0 || remainingF2LMoveCount < 0 || totalRouteMoveCount < 0 || rotationCount < 0) {
            throw new IllegalArgumentException("selection metrics cannot be negative");
        }
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
