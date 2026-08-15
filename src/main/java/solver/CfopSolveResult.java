package solver;

public record CfopSolveResult(
        String scramble,
        String crossFace,
        String f2lMode,
        int f2lSetupCaseCount,
        int f2lInsertCaseCount,
        CfopStageResult cross,
        CfopStageResult f2l,
        CfopStageResult oll,
        CfopStageResult pll,
        String solvedF2LSlots,
        boolean fullySolved,
        double elapsedMs,
        F2LModeComparison modeComparison,
        F2LSolveTrace f2lTrace
) {
    public CfopSolveResult {
        java.util.Objects.requireNonNull(f2lTrace, "f2lTrace");
    }

    public int totalMoveCount() {
        return cross.moveCount() + f2l.moveCount() + oll.moveCount() + pll.moveCount();
    }
}
