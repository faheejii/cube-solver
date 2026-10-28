package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_enabled_trial_revision_ids")
public class UserEnabledTrialRevisionEntity {
    @EmbeddedId
    private UserTrialRevisionPreferenceId id;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected UserEnabledTrialRevisionEntity() {}

    public UserEnabledTrialRevisionEntity(UserTrialRevisionPreferenceId id, OffsetDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UserTrialRevisionPreferenceId getId() { return id; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
