package database.persistence.repository;

import java.time.OffsetDateTime;

/** Native projection used by the later history service; it keeps cursor ordering in SQL. */
public interface HistoryRowProjection {
    Long getId();
    String getClientAttemptId();
    String getScramble();
    String getCrossFaceRequested();
    Integer getTimerMs();
    Integer getOfficialMs();
    String getPenalty();
    Boolean getDnf();
    OffsetDateTime getCreatedAt();
    String getFastCross();
    String getOptimizedCross();
}
