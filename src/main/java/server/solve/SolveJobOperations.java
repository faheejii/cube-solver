package server.solve;

import api.SolveApiRequest;

import java.util.List;

/** Operations exposed to HTTP adapters without exposing solve-job implementation state. */
public interface SolveJobOperations {
    SolveJobSnapshot submit(
            SolveApiRequest request, String userId, Long solveId, boolean saveOnComplete,
            List<String> disabledAlgorithms
    );
    default SolveJobSnapshot submit(
            SolveApiRequest request, String userId, Long solveId, boolean saveOnComplete,
            SolverSnapshot solverSnapshot
    ) {
        return submit(request, userId, solveId, saveOnComplete,
                solverSnapshot == null ? List.of() : solverSnapshot.legacyDisabledAlgorithms());
    }
    default SolveJobSnapshot submit(SolveApiRequest request, String userId, Long solveId, boolean saveOnComplete) {
        return submit(request, userId, solveId, saveOnComplete, List.of());
    }
    SolveJobSnapshot find(String jobId, String requesterUserId);
    SolveJobSnapshot cancel(String jobId, String requesterUserId);
    void cancelLinkedJobs(String userId, long solveId);
    void cancelOwnedJobs(String userId);

    class CapacityException extends IllegalStateException {
        public CapacityException(String message) { super(message); }
    }

    class AuthenticationRequiredException extends IllegalStateException {
        public AuthenticationRequiredException() { super("Authentication required"); }
    }

    class ForbiddenException extends IllegalStateException {
        public ForbiddenException() { super("Solve job belongs to another user"); }
    }
}
