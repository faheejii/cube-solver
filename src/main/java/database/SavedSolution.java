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
        OffsetDateTime updatedAt
) {
}
