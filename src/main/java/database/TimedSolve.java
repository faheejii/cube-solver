package database;

import java.time.OffsetDateTime;

public record TimedSolve(
        long id,
        Integer officialMs,
        boolean dnf,
        OffsetDateTime createdAt
) {
}
