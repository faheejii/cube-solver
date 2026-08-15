package api;

import com.fasterxml.jackson.databind.ObjectMapper;

public record SaveSolutionApiRequest(
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
        String pllStatus,
        String f2lTraceJson,
        String comparisonJson
) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public SaveSolutionApiRequest {
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
        if (f2lSetupCaseCount < 0 || f2lInsertCaseCount < 0 || totalMoves < 0
                || crossMoves < 0 || f2lMoves < 0 || ollMoves < 0 || pllMoves < 0
                || !Double.isFinite(solveElapsedMs) || solveElapsedMs < 0) {
            throw new IllegalArgumentException("solution metrics must be non-negative and finite");
        }
        requireMetadata(f2lTraceJson, "f2lTraceJson");
        validateMetadata(comparisonJson, "comparisonJson");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }

    private static void validateMetadata(String value, String field) {
        if (value != null && value.length() > 512_000) {
            throw new IllegalArgumentException(field + " is too large");
        }
        if (value != null) {
            try {
                var node = JSON.readTree(value);
                if (node == null || !node.isObject()) {
                    throw new IllegalArgumentException(field + " must be a JSON object");
                }
            } catch (java.io.IOException exception) {
                throw new IllegalArgumentException(field + " must be valid JSON", exception);
            }
        }
    }

    private static void requireMetadata(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        validateMetadata(value, field);
    }
}
