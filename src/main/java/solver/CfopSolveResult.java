package solver;

public record CfopSolveResult(
        String scramble,
        String crossFace,
        boolean useLegacyF2L,
        String f2lMode,
        int f2lSetupCaseCount,
        int f2lInsertCaseCount,
        CfopStageResult cross,
        CfopStageResult f2l,
        CfopStageResult oll,
        CfopStageResult pll,
        String solvedF2LSlots,
        boolean fullySolved,
        double elapsedMs
) {
    public int totalMoveCount() {
        return cross.moveCount() + f2l.moveCount() + oll.moveCount() + pll.moveCount();
    }
}
