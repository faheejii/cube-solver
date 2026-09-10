package database.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "solves", indexes = {
        @Index(name = "idx_solves_user_created_at", columnList = "user_id, created_at"),
        @Index(name = "idx_solves_user_official_ms", columnList = "user_id, official_ms"),
        @Index(name = "idx_solves_user_session_created_at", columnList = "user_id, session_id, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_solves_user_client_attempt", columnNames = {"user_id", "client_attempt_id"})
})
public class SolveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SolveSessionEntity session;

    @Column(name = "client_attempt_id", nullable = false)
    private String clientAttemptId;

    @Column(nullable = false)
    private String scramble;

    @Column(name = "cross_face_requested", nullable = false)
    private String crossFaceRequested;

    @Column(name = "timer_ms")
    private Integer timerMs;

    @Column(nullable = false)
    private String penalty;

    @Column(name = "official_ms")
    private Integer officialMs;

    @Column(nullable = false)
    private boolean dnf;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SolveEntity() {
    }

    public SolveEntity(UserEntity user, String clientAttemptId, String scramble,
                       String crossFaceRequested, Integer timerMs, String penalty,
                       Integer officialMs, boolean dnf) {
        this.user = user;
        this.clientAttemptId = clientAttemptId;
        this.scramble = scramble;
        this.crossFaceRequested = crossFaceRequested;
        this.timerMs = timerMs;
        this.penalty = penalty;
        this.officialMs = officialMs;
        this.dnf = dnf;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public UserEntity getUser() { return user; }
    public SolveSessionEntity getSession() { return session; }
    public String getClientAttemptId() { return clientAttemptId; }
    public String getScramble() { return scramble; }
    public String getCrossFaceRequested() { return crossFaceRequested; }
    public Integer getTimerMs() { return timerMs; }
    public String getPenalty() { return penalty; }
    public Integer getOfficialMs() { return officialMs; }
    public boolean isDnf() { return dnf; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
