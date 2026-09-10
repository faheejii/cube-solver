package database.persistence.repository;

public interface UserStatsWriteRepository {
    void updateAfterSolve(long userId, Integer officialMs, boolean dnf);

    void rebuild(long userId);
}
