package server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Lightweight process-local metrics for health checks and operational diagnosis. */
final class OperationalMetrics {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong requestErrors = new AtomicLong();
    private final AtomicLong requestDurationNanos = new AtomicLong();
    private final AtomicInteger inFlightRequests = new AtomicInteger();
    private final AtomicLong jobsSubmitted = new AtomicLong();
    private final AtomicLong jobsStarted = new AtomicLong();
    private final AtomicLong jobsCompleted = new AtomicLong();
    private final AtomicLong jobsFailed = new AtomicLong();
    private final AtomicLong jobsCancelled = new AtomicLong();
    private final AtomicLong jobsTimedOut = new AtomicLong();
    private final AtomicInteger queuedJobs = new AtomicInteger();
    private final AtomicInteger runningJobs = new AtomicInteger();
    private final AtomicLong solveDurationNanos = new AtomicLong();

    void requestStarted() { inFlightRequests.incrementAndGet(); }

    void requestFinished(int statusCode, long durationNanos) {
        requests.incrementAndGet();
        requestDurationNanos.addAndGet(durationNanos);
        inFlightRequests.decrementAndGet();
        if (statusCode >= 500) requestErrors.incrementAndGet();
    }

    void jobSubmitted() {
        jobsSubmitted.incrementAndGet();
        queuedJobs.incrementAndGet();
    }

    void jobStarted() {
        jobsStarted.incrementAndGet();
        queuedJobs.updateAndGet(value -> Math.max(0, value - 1));
        runningJobs.incrementAndGet();
    }

    void jobCompleted(long durationNanos) {
        jobsCompleted.incrementAndGet();
        runningJobs.updateAndGet(value -> Math.max(0, value - 1));
        solveDurationNanos.addAndGet(durationNanos);
    }

    void jobFailed(boolean wasRunning) { jobsFailed.incrementAndGet(); terminalJob(wasRunning); }
    void jobCancelled(boolean wasRunning) { jobsCancelled.incrementAndGet(); terminalJob(wasRunning); }
    void jobTimedOut(boolean wasRunning) { jobsTimedOut.incrementAndGet(); terminalJob(wasRunning); }

    private void terminalJob(boolean wasRunning) {
        if (wasRunning) runningJobs.updateAndGet(value -> Math.max(0, value - 1));
        else queuedJobs.updateAndGet(value -> Math.max(0, value - 1));
    }

    String json() {
        var requestCount = requests.get();
        var solveCount = jobsCompleted.get();
        var requestAverageMs = requestCount == 0 ? 0.0 : nanosToMillis(requestDurationNanos.get()) / requestCount;
        var solveAverageMs = solveCount == 0 ? 0.0 : nanosToMillis(solveDurationNanos.get()) / solveCount;
        return "{"
                + "\"requests\":{"
                + "\"total\":" + requestCount + ",\"errors\":" + requestErrors.get()
                + ",\"inFlight\":" + inFlightRequests.get() + ",\"averageMs\":" + format(requestAverageMs)
                + "},\"solveJobs\":{"
                + "\"submitted\":" + jobsSubmitted.get() + ",\"started\":" + jobsStarted.get()
                + ",\"completed\":" + jobsCompleted.get() + ",\"failed\":" + jobsFailed.get()
                + ",\"cancelled\":" + jobsCancelled.get() + ",\"timedOut\":" + jobsTimedOut.get()
                + ",\"queued\":" + queuedJobs.get() + ",\"running\":" + runningJobs.get()
                + ",\"averageMs\":" + format(solveAverageMs) + "}}";
    }

    private static double nanosToMillis(long nanos) { return nanos / 1_000_000.0; }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
