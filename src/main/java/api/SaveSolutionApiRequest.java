package api;

public record SaveSolutionApiRequest(
        String userId,
        String crossFaceRequested,
        String crossFaceChosen,
        String f2lMode,
        Integer f2lSetupCaseCount,
        Integer f2lInsertCaseCount,
        String solvedF2LSlots,
        Integer totalMoves,
        boolean fullySolved,
        Double solveElapsedMs,
        String crossAlgorithm,
        Integer crossMoves,
        boolean crossSolved,
        String crossStatus,
        String f2lAlgorithm,
        Integer f2lMoves,
        boolean f2lSolved,
        String f2lStatus,
        String ollAlgorithm,
        Integer ollMoves,
        boolean ollSolved,
        String ollStatus,
        String pllAlgorithm,
        Integer pllMoves,
        boolean pllSolved,
        String pllStatus
) {
    public SaveSolutionApiRequest {
        requireText(userId, "userId");
        requireText(crossFaceRequested, "crossFaceRequested");
        requireText(crossFaceChosen, "crossFaceChosen");
        requireText(f2lMode, "f2lMode");
        requireText(solvedF2LSlots, "solvedF2LSlots");
        requireText(crossStatus, "crossStatus");
        requireText(f2lStatus, "f2lStatus");
        requireText(ollStatus, "ollStatus");
        requireText(pllStatus, "pllStatus");
        if (crossAlgorithm == null || f2lAlgorithm == null || ollAlgorithm == null || pllAlgorithm == null) {
            throw new IllegalArgumentException("stage algorithms cannot be null");
        }
        if (f2lSetupCaseCount == null || f2lInsertCaseCount == null || totalMoves == null
                || solveElapsedMs == null || crossMoves == null || f2lMoves == null
                || ollMoves == null || pllMoves == null) {
            throw new IllegalArgumentException("solution metrics cannot be null");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
