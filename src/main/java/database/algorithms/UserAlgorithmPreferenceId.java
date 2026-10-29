package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserAlgorithmPreferenceId implements Serializable {
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "algorithm_id")
    private UUID algorithmId;

    protected UserAlgorithmPreferenceId() {}

    public UserAlgorithmPreferenceId(Long userId, UUID algorithmId) {
        this.userId = userId;
        this.algorithmId = algorithmId;
    }

    public Long getUserId() { return userId; }
    public UUID getAlgorithmId() { return algorithmId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserAlgorithmPreferenceId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(algorithmId, that.algorithmId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, algorithmId); }
}
