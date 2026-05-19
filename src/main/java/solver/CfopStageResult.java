package solver;

public record CfopStageResult(
        String name,
        String algorithm,
        int moveCount,
        boolean solved,
        String status
) {
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
    }
}
