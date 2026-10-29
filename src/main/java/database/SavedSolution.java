package database;

import solver.CfopStageResult;

import java.time.OffsetDateTime;

public record SavedSolution(
        String mode,
        String crossFaceRequested,
        String crossFaceChosen,
        int f2lSetupCaseCount,
        int f2lInsertCaseCount,
        CfopStageResult cross,
        CfopStageResult f2l,
        CfopStageResult oll,
        CfopStageResult pll,
        String solvedF2LSlots,
        boolean fullySolved,
        int totalMoves,
        double elapsedMs,
        String solverVersion,
        OffsetDateTime updatedAt,
        String f2lTraceJson,
        String comparisonJson,
        String disabledAlgorithmsJson
) {
    public SavedSolution(
            String mode, String crossFaceRequested, String crossFaceChosen,
            int f2lSetupCaseCount, int f2lInsertCaseCount,
            CfopStageResult cross, CfopStageResult f2l, CfopStageResult oll, CfopStageResult pll,
            String solvedF2LSlots, boolean fullySolved, int totalMoves, double elapsedMs,
            String solverVersion, OffsetDateTime updatedAt, String f2lTraceJson, String comparisonJson
    ) {
        this(mode, crossFaceRequested, crossFaceChosen, f2lSetupCaseCount, f2lInsertCaseCount,
                cross, f2l, oll, pll, solvedF2LSlots, fullySolved, totalMoves, elapsedMs,
                solverVersion, updatedAt, f2lTraceJson, comparisonJson, null);
    }
}
