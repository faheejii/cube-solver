package database;

import java.time.OffsetDateTime;
import java.util.List;

public record SolveHistoryDetail(
        long id,
        String clientAttemptId,
        String scramble,
        String crossFaceRequested,
        Integer timerMs,
        Integer officialMs,
        String penalty,
        boolean dnf,
        OffsetDateTime createdAt,
        List<SavedSolution> solutions
) {
}
