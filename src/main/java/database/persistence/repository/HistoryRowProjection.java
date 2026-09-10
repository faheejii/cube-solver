package database.persistence.repository;

import java.time.Instant;

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
    Instant getCreatedAt();
    String getFastCross();
    String getOptimizedCross();
}
