package solver;

/** Signals that optional comparison work exceeded its allotted time. */
public final class SolveBudgetExceededException extends RuntimeException {
    public SolveBudgetExceededException() {
        super("Solve time budget exceeded");
    }
}
