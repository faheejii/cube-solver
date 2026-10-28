package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_disabled_algorithm_ids")
public class UserDisabledAlgorithmEntity {
    @EmbeddedId
    private UserAlgorithmPreferenceId id;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected UserDisabledAlgorithmEntity() {}

    public UserDisabledAlgorithmEntity(UserAlgorithmPreferenceId id, OffsetDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UserAlgorithmPreferenceId getId() { return id; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
