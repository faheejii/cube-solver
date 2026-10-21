package server.solve;

/** Narrow metrics callbacks used by asynchronous solve execution. */
public interface SolveJobMetrics {
    void jobSubmitted();
    void jobStarted();
    void jobCompleted(long durationNanos);
    void jobFailed(boolean wasRunning);
    void jobCancelled(boolean wasRunning);
    void jobTimedOut(boolean wasRunning);
}
