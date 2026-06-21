package solver;

public final class SolveCancellation {
    private SolveCancellation() {
    }

    public static void throwIfCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new SolveCancelledException();
        }
    }
}
