package database.persistence.repository;

import java.time.Instant;

public interface TimedSolveProjection {
    Long getId();
    Integer getOfficialMs();
    Boolean getDnf();
    Instant getCreatedAt();
}
