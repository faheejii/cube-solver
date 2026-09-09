package database.persistence.repository;

import java.time.OffsetDateTime;

public interface TimedSolveProjection {
    Long getId();
    Integer getOfficialMs();
    Boolean getDnf();
    OffsetDateTime getCreatedAt();
}
