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
        F2LModeComparison modeComparison
) {
    public CfopSolveResult(
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
            double elapsedMs
    ) {
        this(
                scramble, crossFace, f2lMode, f2lSetupCaseCount, f2lInsertCaseCount,
                cross, f2l, oll, pll, solvedF2LSlots, fullySolved, elapsedMs, null
        );
    }

    public int totalMoveCount() {
        return cross.moveCount() + f2l.moveCount() + oll.moveCount() + pll.moveCount();
    }
}
