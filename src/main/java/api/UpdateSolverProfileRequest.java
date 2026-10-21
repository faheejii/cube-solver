package api;

import java.util.List;

public record UpdateSolverProfileRequest(List<String> disabledAlgorithms) {
    public UpdateSolverProfileRequest {
        disabledAlgorithms = disabledAlgorithms == null ? List.of() : List.copyOf(disabledAlgorithms);
    }
}
