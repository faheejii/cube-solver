package api;

public record CreateSolveJobRequest(
        String scramble,
        String crossFace,
        String f2lMode,
        Long solveId,
        boolean saveOnComplete,
        Long deadlineSeconds
) {
    public CreateSolveJobRequest {
        SolveApiRequest.validateDeadlineSeconds(deadlineSeconds);
    }

    public CreateSolveJobRequest(
            String scramble,
            String crossFace,
            String f2lMode,
            Long solveId,
            boolean saveOnComplete
    ) {
        this(scramble, crossFace, f2lMode, solveId, saveOnComplete, null);
    }

    public SolveApiRequest solveRequest() {
        return new SolveApiRequest(scramble, crossFace, f2lMode, deadlineSeconds);
    }
}
