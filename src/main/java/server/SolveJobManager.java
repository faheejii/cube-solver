package server;

import api.SolveApiRequest;
import database.DatabaseManager;
import database.SaveSolutionCommand;
import database.SolveHistoryRepository;
import solver.CfopSolveResult;
import solver.F2LMode;
import solver.SolveCancellation;
import solver.SolveCancelledException;
import solver.SolveDeadlineExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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

final class SolveJobManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolveJobManager.class);
    private static final int MAX_RETAINED_FINISHED_JOBS = 100;

    private final solver.CfopSolveService solveService;
    private final DatabaseManager databaseManager;
    private final SolveHistoryRepository repository;
    private final OperationalMetrics operationalMetrics;
    private final Long solveDeadlineOverrideNanos;
    private final ExecutorService optimizedExecutor;
    private final ExecutorService fastExecutor;
    private final Map<String, JobState> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> finishedJobIds = new ConcurrentLinkedDeque<>();

    SolveJobManager(solver.CfopSolveService solveService, DatabaseManager databaseManager) {
        this(solveService, databaseManager, null, new OperationalMetrics());
    }

    SolveJobManager(
            solver.CfopSolveService solveService,
            DatabaseManager databaseManager,
            long solveDeadlineNanos
    ) {
        this(solveService, databaseManager, solveDeadlineNanos, new OperationalMetrics());
    }

    SolveJobManager(
            solver.CfopSolveService solveService,
            DatabaseManager databaseManager,
            OperationalMetrics operationalMetrics
    ) {
        this(solveService, databaseManager, null, operationalMetrics);
    }

    private SolveJobManager(
            solver.CfopSolveService solveService,
            DatabaseManager databaseManager,
            Long solveDeadlineOverrideNanos,
            OperationalMetrics operationalMetrics
    ) {
        this.solveService = solveService;
        this.databaseManager = databaseManager;
        this.repository = new SolveHistoryRepository(databaseManager);
        this.operationalMetrics = operationalMetrics;
        if (solveDeadlineOverrideNanos != null && solveDeadlineOverrideNanos <= 0) {
            throw new IllegalArgumentException("solveDeadlineOverrideNanos must be positive");
        }
        this.solveDeadlineOverrideNanos = solveDeadlineOverrideNanos;
        this.optimizedExecutor = boundedExecutor("optimized-solve-worker", 1, configuredQueueSize("server.optimized.queue", 4));
        this.fastExecutor = boundedExecutor("fast-solve-worker", 2, configuredQueueSize("server.fast.queue", 16));
    }

    JobSnapshot submit(
            SolveApiRequest apiRequest,
            String userId,
            Long solveId,
            boolean saveOnComplete
    ) {
        var request = apiRequest.toSolveRequest();
        validateSaveRequest(userId, solveId, saveOnComplete);
        long solveDeadlineNanos = solveDeadlineNanos(apiRequest);

        var job = new JobState(
                UUID.randomUUID().toString(),
                userId,
                solveId,
                saveOnComplete,
                System.nanoTime() + solveDeadlineNanos
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
            throw new CapacityException("Solve queue is full; try again shortly");
        }
        return job.snapshot();
    }

    long solveDeadlineNanos(SolveApiRequest apiRequest) {
        if (solveDeadlineOverrideNanos != null) {
            return solveDeadlineOverrideNanos;
        }
        return TimeUnit.SECONDS.toNanos(apiRequest.effectiveDeadlineSeconds());
    }

    JobSnapshot find(String jobId) {
        var job = requireJob(jobId);
        if (job.expireIfQueued()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    JobSnapshot find(String jobId, String requesterUserId) {
        var job = requireJob(jobId);
        requireOwner(job, requesterUserId);
        if (job.expireIfQueued()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    JobSnapshot cancel(String jobId) {
        var job = requireJob(jobId);
        if (job.cancel()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    JobSnapshot cancel(String jobId, String requesterUserId) {
        var job = requireJob(jobId);
        requireOwner(job, requesterUserId);
        if (job.cancel()) {
            retainFinished(job);
        }
        return job.snapshot();
    }

    void cancelLinkedJobs(String userId, long solveId) {
        for (var job : jobsById.values()) {
            if (job.targetsSavedSolve(userId, solveId) && job.cancel()) {
                retainFinished(job);
            }
        }
    }

    void cancelOwnedJobs(String userId) {
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
                    solveService.solveWithProgress(request, progress -> {
                        SolveCancellation.throwIfCancelled();
                        job.updateProgress(progress);
                    })
            );
            SolveCancellation.throwIfCancelled();
            CheckedRunnable saveAction = saveOnComplete
                    ? () -> repository.upsertSolution(toSaveCommand(
                        userId,
                        solveId,
                        requestedCrossFace,
                        result
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
            throw new AuthenticationRequiredException();
        }
        if (!java.util.Objects.equals(job.userId, requesterUserId)) {
            throw new ForbiddenException();
        }
    }

    private void validateSaveRequest(String userId, Long solveId, boolean saveOnComplete) {
        if (!saveOnComplete) {
            return;
        }
        if (!databaseManager.isConfigured()) {
            throw new IllegalArgumentException("Database is not configured");
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
        optimizedExecutor.shutdownNow();
        fastExecutor.shutdownNow();
    }

    static final class CapacityException extends IllegalStateException {
        private CapacityException(String message) {
            super(message);
        }
    }

    static final class AuthenticationRequiredException extends IllegalStateException {
        private AuthenticationRequiredException() {
            super("Authentication required");
        }
    }

    static final class ForbiddenException extends IllegalStateException {
        private ForbiddenException() {
            super("Solve job belongs to another user");
        }
    }

    private static SaveSolutionCommand toSaveCommand(
            String userId,
            long solveId,
            String requestedCrossFace,
            CfopSolveResult result
    ) {
        var solution = combinedSolution(result);
        return new SaveSolutionCommand(
                userId,
                solveId,
                result.f2lMode(),
                requestedCrossFace,
                result.crossFace(),
                solution,
                solution,
                result.f2lSetupCaseCount(),
                result.f2lInsertCaseCount(),
                result.solvedF2LSlots(),
                result.totalMoveCount(),
                result.fullySolved(),
                result.elapsedMs(),
                result.cross().algorithm(),
                result.cross().moveCount(),
                result.cross().solved(),
                result.cross().status(),
                result.f2l().algorithm(),
                result.f2l().moveCount(),
                result.f2l().solved(),
                result.f2l().status(),
                result.oll().algorithm(),
                result.oll().moveCount(),
                result.oll().solved(),
                result.oll().status(),
                result.pll().algorithm(),
                result.pll().moveCount(),
                result.pll().solved(),
                result.pll().status(),
                DatabaseManager.SOLVER_VERSION,
                JsonSupport.f2lTraceJson(result.f2l(), result.f2lTrace()),
                JsonSupport.modeComparisonJson(result.modeComparison())
        );
    }

    private static String normalizedCrossFace(String crossFace) {
        return crossFace == null || crossFace.isBlank() ? "U" : crossFace.trim().toUpperCase();
    }

    private static String combinedSolution(CfopSolveResult result) {
        return java.util.stream.Stream.of(
                        result.cross().algorithm(),
                        result.f2l().algorithm(),
                        result.oll().algorithm(),
                        result.pll().algorithm()
                )
                .filter(algorithm -> algorithm != null && !algorithm.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    record JobSnapshot(
            String id,
            String status,
            long statesExplored,
            long statesPruned,
            long duplicateStates,
            int bestMoves,
            int completedCandidates,
            int candidatesEvaluated,
            int bestTotalMoves,
            String phase,
            String currentCrossFace,
            int completedCrosses,
            int totalCrosses,
            int optimizationCandidate,
            int totalOptimizationCandidates,
            boolean optimizationBudgetExpired,
            CfopSolveResult result,
            String error
    ) {
    }

    private static final class JobState {
        private final String id;
        private final String userId;
        private final Long solveId;
        private final boolean saveOnComplete;
        private final long expiresAtNanos;
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

        private JobState(String id, String userId, Long solveId, boolean saveOnComplete, long expiresAtNanos) {
            this.id = id;
            this.userId = userId;
            this.solveId = solveId;
            this.saveOnComplete = saveOnComplete;
            this.expiresAtNanos = expiresAtNanos;
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

        private JobSnapshot snapshot() {
            return new JobSnapshot(
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
                    error
            );
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
