package solver;

public record CfopSolveResult(
        String scramble,
        String crossFace,
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
