package api;

import java.util.Set;

public record UpdateSolvePenaltyRequest(String penalty) {
    public UpdateSolvePenaltyRequest {
        if (penalty == null || !Set.of("none", "+2", "dnf").contains(penalty)) {
            throw new IllegalArgumentException("penalty must be none, +2, or dnf");
        }
    }
}
