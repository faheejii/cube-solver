package api;

import com.fasterxml.jackson.databind.JsonNode;

/** Jackson-bound Spring request preserving the API's structured metadata fields. */
public record SpringSaveSolutionRequest(
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
        JsonNode f2lTraceJson,
        JsonNode comparisonJson
) {
    public SaveSolutionApiRequest toApiRequest() {
        return new SaveSolutionApiRequest(
                crossFaceRequested, crossFaceChosen, f2lMode,
                f2lSetupCaseCount, f2lInsertCaseCount, solvedF2LSlots,
                totalMoves, fullySolved, solveElapsedMs,
                crossAlgorithm, crossMoves, crossSolved, crossStatus,
                f2lAlgorithm, f2lMoves, f2lSolved, f2lStatus,
                ollAlgorithm, ollMoves, ollSolved, ollStatus,
                pllAlgorithm, pllMoves, pllSolved, pllStatus,
                metadata(f2lTraceJson), metadata(comparisonJson)
        );
    }

    private static String metadata(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }
}
