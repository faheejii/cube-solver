package solver;

/** Signals that the complete solve deadline was reached. */
public final class SolveDeadlineExceededException extends RuntimeException {
    public SolveDeadlineExceededException() {
        super("Solve deadline exceeded");
    }
}
