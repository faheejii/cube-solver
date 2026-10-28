package api;

import java.util.List;
import java.util.UUID;

public record UpdateSolverProfileRequest(
        List<String> disabledAlgorithms,
        List<UUID> disabledAlgorithmIds,
        List<UUID> enabledTrialRevisionIds
) {
    public UpdateSolverProfileRequest(List<String> disabledAlgorithms) {
        this(disabledAlgorithms, null, null);
    }
}
