package database;

import java.time.OffsetDateTime;

public record SolveHistoryEntry(
        long id,
        String clientAttemptId,
        String scramble,
        String crossFaceRequested,
        Integer timerMs,
        Integer officialMs,
        String penalty,
        boolean dnf,
        String fastCrossFaceRequested,
        String optimizedCrossFaceRequested,
        OffsetDateTime createdAt
) {
}
