package database.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_stats")
public class UserStatsEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "solve_count", nullable = false)
    private int solveCount;
    @Column(name = "dnf_count", nullable = false)
    private int dnfCount;
    @Column(name = "best_single_ms")
    private Integer bestSingleMs;
    @Column(name = "latest_official_ms")
    private Integer latestOfficialMs;
    @Column(name = "latest_solve_at")
    private OffsetDateTime latestSolveAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserStatsEntity() {
    }

    public Long getUserId() { return userId; }
    public UserEntity getUser() { return user; }
    public int getSolveCount() { return solveCount; }
    public int getDnfCount() { return dnfCount; }
    public Integer getBestSingleMs() { return bestSingleMs; }
    public Integer getLatestOfficialMs() { return latestOfficialMs; }
    public OffsetDateTime getLatestSolveAt() { return latestSolveAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
