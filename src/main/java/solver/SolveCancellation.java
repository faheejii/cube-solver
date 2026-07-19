package solver;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class SolveCancellation {
    private static final ThreadLocal<BooleanSupplier> STOP_SIGNAL = new ThreadLocal<>();
    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private SolveCancellation() {
    }

    public static void throwIfCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new SolveCancelledException();
        }
        var deadline = DEADLINE_NANOS.get();
        if (deadline != null && System.nanoTime() >= deadline) {
            throw new SolveDeadlineExceededException();
        }
        var stopSignal = STOP_SIGNAL.get();
        if (stopSignal != null && stopSignal.getAsBoolean()) {
            throw new SolveBudgetExceededException();
        }
    }

    public static <T> T withStopSignal(BooleanSupplier stopSignal, Supplier<T> action) {
        var previous = STOP_SIGNAL.get();
        STOP_SIGNAL.set(() -> (previous != null && previous.getAsBoolean())
                || (stopSignal != null && stopSignal.getAsBoolean()));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                STOP_SIGNAL.remove();
            } else {
                STOP_SIGNAL.set(previous);
            }
        }
    }

    public static <T> T withDeadline(long durationNanos, Supplier<T> action) {
        var requestedDeadline = System.nanoTime() + durationNanos;
        var previous = DEADLINE_NANOS.get();
        DEADLINE_NANOS.set(previous == null ? requestedDeadline : Math.min(previous, requestedDeadline));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                DEADLINE_NANOS.remove();
            } else {
                DEADLINE_NANOS.set(previous);
            }
        }
    }
}
