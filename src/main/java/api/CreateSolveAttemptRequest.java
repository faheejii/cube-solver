package api;

public record CreateSolveAttemptRequest(
        String clientAttemptId,
        String scramble,
        String crossFaceRequested,
        Integer timerMs,
        String penalty,
        Integer officialMs,
        boolean dnf
) {
    public CreateSolveAttemptRequest {
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
        if (!java.util.Set.of("none", "+2", "dnf").contains(penalty)) {
            throw new IllegalArgumentException("penalty must be none, +2, or dnf");
        }
        if (timerMs != null && timerMs < 0) {
            throw new IllegalArgumentException("timerMs cannot be negative");
        }
        if (officialMs != null && officialMs < 0) {
            throw new IllegalArgumentException("officialMs cannot be negative");
        }
        if (dnf != penalty.equals("dnf")) {
            throw new IllegalArgumentException("dnf must match the penalty");
        }
        if (dnf != (officialMs == null)) {
            throw new IllegalArgumentException("officialMs must be null only for DNF attempts");
        }
    }
}
