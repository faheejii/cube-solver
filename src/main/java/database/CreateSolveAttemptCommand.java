package database;

public record CreateSolveAttemptCommand(
        String userExternalId,
        String clientAttemptId,
        String scramble,
        String crossFaceRequested,
        Integer timerMs,
        String penalty,
        Integer officialMs,
        boolean dnf
) {
}
