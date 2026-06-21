package solver;

public class SolveCancelledException extends RuntimeException {
    public SolveCancelledException() {
        super("Solve cancelled");
    }
}
