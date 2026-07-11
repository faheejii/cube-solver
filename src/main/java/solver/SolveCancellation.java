package solver;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class SolveCancellation {
    private static final ThreadLocal<BooleanSupplier> STOP_SIGNAL = new ThreadLocal<>();

    private SolveCancellation() {
    }

    public static void throwIfCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new SolveCancelledException();
        }
        var stopSignal = STOP_SIGNAL.get();
        if (stopSignal != null && stopSignal.getAsBoolean()) {
            throw new SolveBudgetExceededException();
        }
    }

    public static <T> T withStopSignal(BooleanSupplier stopSignal, Supplier<T> action) {
        var previous = STOP_SIGNAL.get();
        STOP_SIGNAL.set(stopSignal);
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
}
