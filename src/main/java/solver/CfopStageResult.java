package solver;

public record CfopStageResult(
        String name,
        String algorithm,
        int moveCount,
        boolean solved,
        String status,
        Integer solvedF2lPairCount
) {
    public CfopStageResult(String name, String algorithm, int moveCount, boolean solved, String status) {
        this(name, algorithm, moveCount, solved, status, null);
    }

    public CfopStageResult {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm cannot be null");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status cannot be null or blank");
        }
        if (solvedF2lPairCount != null
                && (!name.equals("cross") || solvedF2lPairCount < 0 || solvedF2lPairCount > 4)) {
            throw new IllegalArgumentException("solvedF2lPairCount must be between 0 and 4 and only apply to the cross stage");
        }
    }
}
