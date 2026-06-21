package api;

public record CreateSolveJobRequest(
        String scramble,
        String crossFace,
        String f2lMode,
        String userId,
        Long solveId,
        boolean saveOnComplete
) {
    public SolveApiRequest solveRequest() {
        return new SolveApiRequest(scramble, crossFace, f2lMode);
    }
}
