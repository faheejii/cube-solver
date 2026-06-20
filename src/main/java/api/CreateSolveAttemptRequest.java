package api;

public record CreateSolveAttemptRequest(
        String userId,
        String clientAttemptId,
        String scramble,
        String crossFaceRequested,
        Integer timerMs,
        String penalty,
        Integer officialMs,
        boolean dnf
) {
    public CreateSolveAttemptRequest {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (clientAttemptId == null || clientAttemptId.isBlank()) {
            throw new IllegalArgumentException("clientAttemptId cannot be null or blank");
        }
        if (scramble == null || scramble.isBlank()) {
            throw new IllegalArgumentException("scramble cannot be null or blank");
        }
        if (crossFaceRequested == null || crossFaceRequested.isBlank()) {
            throw new IllegalArgumentException("crossFaceRequested cannot be null or blank");
        }
        if (penalty == null || penalty.isBlank()) {
            throw new IllegalArgumentException("penalty cannot be null or blank");
        }
    }
}
