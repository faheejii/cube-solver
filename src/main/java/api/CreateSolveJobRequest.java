package api;

public record CreateSolveJobRequest(
        String scramble,
        String crossFace,
        String f2lMode,
        Long solveId,
        Boolean saveOnComplete,
        Long deadlineSeconds,
        Boolean deepColorNeutral
) {
    public CreateSolveJobRequest {
        saveOnComplete = Boolean.TRUE.equals(saveOnComplete);
        deepColorNeutral = Boolean.TRUE.equals(deepColorNeutral);
        SolveApiRequest.validateDeadlineSeconds(deadlineSeconds);
    }

    public CreateSolveJobRequest(
            String scramble,
            String crossFace,
            String f2lMode,
            Long solveId,
            boolean saveOnComplete
    ) {
        this(scramble, crossFace, f2lMode, solveId, saveOnComplete, null, false);
    }

    public CreateSolveJobRequest(
            String scramble, String crossFace, String f2lMode,
            Long solveId, boolean saveOnComplete, Long deadlineSeconds
    ) {
        this(scramble, crossFace, f2lMode, solveId, saveOnComplete, deadlineSeconds, false);
    }

    public SolveApiRequest solveRequest() {
        return new SolveApiRequest(scramble, crossFace, f2lMode, deadlineSeconds, deepColorNeutral);
    }
}
