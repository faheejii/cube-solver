package server.solve;

import api.SolveApiRequest;
import solver.CfopSolveResult;
import solver.F2LMode;
import solver.SolveCancellation;
import solver.SolveCancelledException;
import solver.SolveDeadlineExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class SolveJobManager implements SolveJobOperations, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolveJobManager.class);
    private static final int MAX_RETAINED_FINISHED_JOBS = 100;

    private final solver.CfopSolveService solveService;
    private final CompletedSolutionPersistence completedSolutionPersistence;
    private final SolveJobMetrics operationalMetrics;
    private final SolveJobSaveCommandFactory saveCommandFactory;
    private final SolverProfileService solverProfiles;
    private final Long solveDeadlineOverrideNanos;
    private final ExecutorService optimizedExecutor;
    private final ExecutorService fastExecutor;
    private final boolean ownsExecutors;
    private final Map<String, JobState> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> finishedJobIds = new ConcurrentLinkedDeque<>();

    SolveJobManager(
            solver.CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence
    ) {
        this(solveService, completedSolutionPersistence, NoopMetrics.INSTANCE);
    }

    SolveJobManager(
            solver.CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence,
            SolveJobMetrics operationalMetrics
    ) {
        this(solveService, completedSolutionPersistence, null, operationalMetrics, null, null, true,
                NoopSaveCommandFactory.INSTANCE, null);
    }

    SolveJobManager(
            solver.CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence,
            SolveJobMetrics operationalMetrics,
            long solveDeadlineNanos
    ) {
        this(solveService, completedSolutionPersistence, solveDeadlineNanos, operationalMetrics, null, null, true,
                NoopSaveCommandFactory.INSTANCE, null);
    }

    SolveJobManager(
            solver.CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence,
            SolveJobMetrics operationalMetrics,
            ExecutorService optimizedExecutor,
            ExecutorService fastExecutor
    ) {
        this(solveService, completedSolutionPersistence, null, operationalMetrics,
                optimizedExecutor, fastExecutor, false, NoopSaveCommandFactory.INSTANCE, null);
    }

    SolveJobManager(
            solver.CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence,
            SolveJobMetrics operationalMetrics,
            SolveJobSaveCommandFactory saveCommandFactory,
            ExecutorService optimizedExecutor,
            ExecutorService fastExecutor
    ) {
        this(solveService, completedSolutionPersistence, null, operationalMetrics,
                optimizedExecutor, fastExecutor, false, saveCommandFactory, null);
    }

    SolveJobManager(
            solver.CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence,
            SolveJobMetrics operationalMetrics,
            SolveJobSaveCommandFactory saveCommandFactory,
            ExecutorService optimizedExecutor,
            ExecutorService fastExecutor,
            SolverProfileService solverProfiles
    ) {
        this(solveService, completedSolutionPersistence, null, operationalMetrics,
                optimizedExecutor, fastExecutor, false, saveCommandFactory, solverProfiles);
    }

    private SolveJobManager(
            solver.CfopSolveService solveService,
            CompletedSolutionPersistence completedSolutionPersistence,
            Long solveDeadlineOverrideNanos,
            SolveJobMetrics operationalMetrics,
            ExecutorService optimizedExecutor,
            ExecutorService fastExecutor,
            boolean ownsExecutors,
            SolveJobSaveCommandFactory saveCommandFactory,
            SolverProfileService solverProfiles
    ) {
        this.solveService = solveService;
        this.solverProfiles = solverProfiles == null ? new SolverProfileService(solveService) : solverProfiles;
        this.completedSolutionPersistence = java.util.Objects.requireNonNull(
                completedSolutionPersistence,
                "completedSolutionPersistence"
        );
        this.operationalMetrics = operationalMetrics;
        this.saveCommandFactory = saveCommandFactory;
        if (solveDeadlineOverrideNanos != null && solveDeadlineOverrideNanos <= 0) {
            throw new IllegalArgumentException("solveDeadlineOverrideNanos must be positive");
        }
        this.solveDeadlineOverrideNanos = solveDeadlineOverrideNanos;
        this.optimizedExecutor = optimizedExecutor == null
                ? boundedExecutor("optimized-solve-worker", 1, configuredQueueSize("server.optimized.queue", 4))
                : java.util.Objects.requireNonNull(optimizedExecutor, "optimizedExecutor");
        this.fastExecutor = fastExecutor == null
                ? boundedExecutor("fast-solve-worker", 2, configuredQueueSize("server.fast.queue", 16))
                : java.util.Objects.requireNonNull(fastExecutor, "fastExecutor");
        this.ownsExecutors = ownsExecutors;
    }

    @Override
    public SolveJobSnapshot submit(
            SolveApiRequest apiRequest,
            String userId,
            Long solveId,
            boolean saveOnComplete
    ) {
        return submit(apiRequest, userId, solveId, saveOnComplete, List.of());
    }

    @Override
    public SolveJobSnapshot submit(
            SolveApiRequest apiRequest,
            String userId,
            Long solveId,
            boolean saveOnComplete,
            List<String> disabledAlgorithms
    ) {
        var request = apiRequest.toSolveRequest();
        var solveProfile = solverProfiles.validate(disabledAlgorithms);
        validateSaveRequest(userId, solveId, saveOnComplete);
        long solveDeadlineNanos = solveDeadlineNanos(apiRequest);

        var job = new JobState(
                UUID.randomUUID().toString(),
                userId,
                solveId,
                saveOnComplete,
                System.nanoTime() + solveDeadlineNanos,
                solveProfile
        );
        jobsById.put(job.id, job);
        operationalMetrics.jobSubmitted();
        var executor = request.f2lMode() == F2LMode.OPTIMIZED ? optimizedExecutor : fastExecutor;
        try {
            job.future = executor.submit(() -> run(
                    job,
                    request,
                    normalizedCrossFace(apiRequest.crossFace()),
                    userId,
                    solveId,
                    saveOnComplete
            ));
        } catch (RejectedExecutionException exception) {
            jobsById.remove(job.id);
            throw new SolveJobOperations.CapacityException("Solve queue is full; try again shortly");
        }
        return job.snapshot();
    }

    long solveDeadlineNanos(SolveApiRequest apiRequest) {
        if (solveDeadlineOverrideNanos != null) {
            return solveDeadlineOverrideNanos;
        }
        return TimeUnit.SECONDS.toNanos(apiRequest.effectiveDeadlineSeconds());
    }

    SolveJobSnapshot find(String jobId) {
        var job = requireJob(jobId);
        if (job.expireIfQueued()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    @Override
    public SolveJobSnapshot find(String jobId, String requesterUserId) {
        var job = requireJob(jobId);
        requireOwner(job, requesterUserId);
        if (job.expireIfQueued()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    SolveJobSnapshot cancel(String jobId) {
        var job = requireJob(jobId);
        if (job.cancel()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    @Override
    public SolveJobSnapshot cancel(String jobId, String requesterUserId) {
        var job = requireJob(jobId);
        requireOwner(job, requesterUserId);
        if (job.cancel()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    @Override
    public void cancelLinkedJobs(String userId, long solveId) {
        for (var job : jobsById.values()) {
            if (job.targetsSavedSolve(userId, solveId) && job.cancel()) {
                retainFinished(job);
            }
        }
    }

    @Override
    public void cancelOwnedJobs(String userId) {
        if (userId == null) {
            return;
        }
        for (var job : jobsById.values()) {
            if (java.util.Objects.equals(job.userId, userId) && job.cancel()) {
                retainFinished(job);
            }
        }
    }

    private void run(
            JobState job,
            solver.CfopSolveRequest request,
            String requestedCrossFace,
            String userId,
            Long solveId,
            boolean saveOnComplete
    ) {
        if (!job.markRunning()) {
            if (job.isTerminal()) {
                retainFinished(job);
            }
            return;
        }
        operationalMetrics.jobStarted();
        long startedAt = System.nanoTime();
        try {
            var result = SolveCancellation.withDeadline(job.remainingNanos(), () ->
                    solverProfiles.solverFor(job.disabledAlgorithms).solveWithProgress(request, progress -> {
                        SolveCancellation.throwIfCancelled();
                        job.updateProgress(progress);
                    })
            );
            SolveCancellation.throwIfCancelled();
            CheckedRunnable saveAction = saveOnComplete
                    ? () -> completedSolutionPersistence.save(saveCommandFactory.create(
                        userId,
                        solveId,
                        requestedCrossFace,
                        result,
                        job.disabledAlgorithms
                    ))
                    : () -> {
                    };
            if (job.complete(result, saveAction)) {
                operationalMetrics.jobCompleted(System.nanoTime() - startedAt);
                retainFinished(job);
            }
        } catch (SolveCancelledException exception) {
            if (job.cancel()) {
                operationalMetrics.jobCancelled(true);
                retainFinished(job);
            }
        } catch (SolveDeadlineExceededException exception) {
            if (job.timeout()) {
                operationalMetrics.jobTimedOut(true);
                retainFinished(job);
            }
        } catch (Exception exception) {
            StructuredLog.error(LOGGER, "solve_job_failed", exception, Map.of("jobId", job.id));
            if (Thread.currentThread().isInterrupted()) {
                if (job.cancel()) {
                    retainFinished(job);
                }
                return;
            }
            if (job.fail(exception)) {
                operationalMetrics.jobFailed(true);
                retainFinished(job);
            }
        }
    }

    private JobState requireJob(String jobId) {
        var job = jobsById.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Solve job not found");
        }
        return job;
    }

    private static void requireOwner(JobState job, String requesterUserId) {
        if (job.userId == null) {
            return;
        }
        if (requesterUserId == null) {
            throw new SolveJobOperations.AuthenticationRequiredException();
        }
        if (!java.util.Objects.equals(job.userId, requesterUserId)) {
            throw new SolveJobOperations.ForbiddenException();
        }
    }

    private void validateSaveRequest(String userId, Long solveId, boolean saveOnComplete) {
        if (!saveOnComplete) {
            return;
        }
        if (!completedSolutionPersistence.isConfigured()) {
            throw new IllegalArgumentException("Database is not configured");
        }
        if (saveCommandFactory == null) {
            throw new IllegalStateException("Completed-solve command factory is not configured");
        }
        if (userId == null || userId.isBlank() || solveId == null) {
            throw new IllegalArgumentException("userId and solveId are required when saveOnComplete is true");
        }
    }

    private void retainFinished(JobState job) {
        if (!job.markRetained()) {
            return;
        }
        finishedJobIds.addLast(job.id);
        while (finishedJobIds.size() > MAX_RETAINED_FINISHED_JOBS) {
            var expiredId = finishedJobIds.pollFirst();
            if (expiredId != null) {
                jobsById.remove(expiredId);
            }
        }
    }

    private static Thread daemonThread(Runnable runnable, String name) {
        var thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static ExecutorService boundedExecutor(String threadName, int workers, int queueSize) {
        return new ThreadPoolExecutor(
                workers,
                workers,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                runnable -> daemonThread(runnable, threadName),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static int configuredQueueSize(String property, int defaultValue) {
        try {
            return Math.max(1, Integer.parseInt(System.getProperty(property, String.valueOf(defaultValue))));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    @Override
    public void close() {
        if (ownsExecutors) {
            optimizedExecutor.shutdownNow();
            fastExecutor.shutdownNow();
        }
    }

    private static String normalizedCrossFace(String crossFace) {
        return crossFace == null || crossFace.isBlank() ? "U" : crossFace.trim().toUpperCase();
    }

    private enum NoopMetrics implements SolveJobMetrics {
        INSTANCE;
        public void jobSubmitted() { }
        public void jobStarted() { }
        public void jobCompleted(long durationNanos) { }
        public void jobFailed(boolean wasRunning) { }
        public void jobCancelled(boolean wasRunning) { }
        public void jobTimedOut(boolean wasRunning) { }
    }

    private enum NoopSaveCommandFactory implements SolveJobSaveCommandFactory {
        INSTANCE;
        public database.SaveSolutionCommand create(
                String userId,
                long solveId,
                String requestedCrossFace,
                CfopSolveResult result,
                List<String> disabledAlgorithms
        ) {
            return null;
        }
    }

    private static final class JobState {
        private final String id;
        private final String userId;
        private final Long solveId;
        private final boolean saveOnComplete;
        private final long expiresAtNanos;
        private final List<String> disabledAlgorithms;
        private final AtomicLong statesExplored = new AtomicLong();
        private final AtomicLong statesPruned = new AtomicLong();
        private final AtomicLong duplicateStates = new AtomicLong();
        private final java.util.concurrent.atomic.AtomicInteger bestMoves =
                new java.util.concurrent.atomic.AtomicInteger(-1);
        private final java.util.concurrent.atomic.AtomicInteger completedCandidates =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger candidatesEvaluated =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger bestTotalMoves =
                new java.util.concurrent.atomic.AtomicInteger(-1);
        private volatile String phase = solver.F2LSolver.SolvePhase.QUEUED.name();
        private volatile String currentCrossFace = "";
        private final java.util.concurrent.atomic.AtomicInteger completedCrosses =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger totalCrosses =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger optimizationCandidate =
                new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.concurrent.atomic.AtomicInteger totalOptimizationCandidates =
                new java.util.concurrent.atomic.AtomicInteger();
        private volatile boolean optimizationBudgetExpired;
        private volatile String status = "queued";
        private volatile CfopSolveResult result;
        private volatile String error;
        private volatile Future<?> future;
        private boolean retained;

        private JobState(String id, String userId, Long solveId, boolean saveOnComplete, long expiresAtNanos,
                         List<String> disabledAlgorithms) {
            this.id = id;
            this.userId = userId;
            this.solveId = solveId;
            this.saveOnComplete = saveOnComplete;
            this.expiresAtNanos = expiresAtNanos;
            this.disabledAlgorithms = List.copyOf(disabledAlgorithms);
        }

        private synchronized boolean markRunning() {
            if (!"queued".equals(status)) {
                return false;
            }
            if (expireIfQueuedLocked()) {
                return false;
            }
            status = "running";
            return true;
        }

        private synchronized boolean expireIfQueued() {
            return expireIfQueuedLocked();
        }

        private boolean expireIfQueuedLocked() {
            if (!"queued".equals(status) || System.nanoTime() < expiresAtNanos) {
                return false;
            }
            status = "timed_out";
            error = "Solve deadline exceeded";
            var submitted = future;
            if (submitted != null) {
                submitted.cancel(false);
            }
            return true;
        }

        private long remainingNanos() {
            return Math.max(1L, expiresAtNanos - System.nanoTime());
        }

        private synchronized boolean complete(
                CfopSolveResult nextResult,
                CheckedRunnable beforeComplete
        ) throws Exception {
            if (isTerminal()) {
                return false;
            }
            beforeComplete.run();
            result = nextResult;
            status = "completed";
            return true;
        }

        private synchronized boolean fail(Exception exception) {
            if (isTerminal()) {
                return false;
            }
            error = exception.getMessage() == null ? "Solve failed" : exception.getMessage();
            status = "failed";
            return true;
        }

        private synchronized boolean cancel() {
            if (isTerminal()) {
                return false;
            }
            status = "cancelled";
            error = "Solve cancelled";
            var submitted = future;
            if (submitted != null) {
                submitted.cancel(true);
            }
            return true;
        }

        private synchronized boolean timeout() {
            if (isTerminal()) {
                return false;
            }
            status = "timed_out";
            error = "Solve deadline exceeded";
            var submitted = future;
            if (submitted != null) {
                submitted.cancel(true);
            }
            return true;
        }

        private boolean targetsSavedSolve(String expectedUserId, long expectedSolveId) {
            return saveOnComplete
                    && solveId != null
                    && solveId == expectedSolveId
                    && java.util.Objects.equals(userId, expectedUserId)
                    && !isTerminal();
        }

        private void updateProgress(solver.F2LSolver.F2LSearchProgress progress) {
            statesExplored.set(progress.statesExplored());
            statesPruned.set(progress.statesPruned());
            duplicateStates.set(progress.duplicateStates());
            bestMoves.set(progress.bestMoves());
            completedCandidates.set(progress.completedCandidates());
            candidatesEvaluated.set(progress.candidatesEvaluated());
            bestTotalMoves.set(progress.bestTotalMoves());
            phase = progress.phase().name();
            currentCrossFace = progress.currentCrossFace();
            completedCrosses.set(progress.completedCrosses());
            totalCrosses.set(progress.totalCrosses());
            optimizationCandidate.set(progress.optimizationCandidate());
            totalOptimizationCandidates.set(progress.totalOptimizationCandidates());
            optimizationBudgetExpired = progress.optimizationBudgetExpired();
        }

        private synchronized boolean markRetained() {
            if (retained) {
                return false;
            }
            retained = true;
            return true;
        }

        private boolean isTerminal() {
            return "completed".equals(status)
                    || "failed".equals(status)
                    || "cancelled".equals(status)
                    || "timed_out".equals(status);
        }

        private SolveJobSnapshot snapshot() {
            return new SolveJobSnapshot(
                    id,
                    status,
                    statesExplored.get(),
                    statesPruned.get(),
                    duplicateStates.get(),
                    bestMoves.get(),
                    completedCandidates.get(),
                    candidatesEvaluated.get(),
                    bestTotalMoves.get(),
                    phase,
                    currentCrossFace,
                    completedCrosses.get(),
                    totalCrosses.get(),
                    optimizationCandidate.get(),
                    totalOptimizationCandidates.get(),
                    optimizationBudgetExpired,
                    result,
                    error,
                    disabledAlgorithms
            );
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
