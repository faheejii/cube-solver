package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserTrialRevisionPreferenceId implements Serializable {
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "revision_id")
    private UUID revisionId;

    protected UserTrialRevisionPreferenceId() {}

    public UserTrialRevisionPreferenceId(Long userId, UUID revisionId) {
        this.userId = userId;
        this.revisionId = revisionId;
    }

    public Long getUserId() { return userId; }
    public UUID getRevisionId() { return revisionId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserTrialRevisionPreferenceId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(revisionId, that.revisionId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, revisionId); }
}
