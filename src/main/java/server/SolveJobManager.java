package server;

import api.SolveApiRequest;
import database.DatabaseManager;
import database.SaveSolutionCommand;
import database.SolveHistoryRepository;
import solver.CfopSolveResult;
import solver.F2LMode;
import solver.SolveCancellation;
import solver.SolveCancelledException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

final class SolveJobManager {
    private static final int MAX_RETAINED_FINISHED_JOBS = 100;

    private final solver.CfopSolveService solveService;
    private final DatabaseManager databaseManager;
    private final SolveHistoryRepository repository;
    private final ExecutorService optimizedExecutor;
    private final ExecutorService fastExecutor;
    private final Map<String, JobState> jobsById = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> finishedJobIds = new ConcurrentLinkedDeque<>();

    SolveJobManager(solver.CfopSolveService solveService, DatabaseManager databaseManager) {
        this.solveService = solveService;
        this.databaseManager = databaseManager;
        this.repository = new SolveHistoryRepository(databaseManager);
        this.optimizedExecutor = Executors.newSingleThreadExecutor(runnable -> daemonThread(
                runnable,
                "optimized-solve-worker"
        ));
        this.fastExecutor = Executors.newFixedThreadPool(2, runnable -> daemonThread(
                runnable,
                "fast-solve-worker"
        ));
    }

    JobSnapshot submit(
            SolveApiRequest apiRequest,
            String userId,
            Long solveId,
            boolean saveOnComplete
    ) {
        var request = apiRequest.toSolveRequest();
        validateSaveRequest(userId, solveId, saveOnComplete);

        var job = new JobState(
                UUID.randomUUID().toString(),
                userId,
                solveId,
                saveOnComplete
        );
        jobsById.put(job.id, job);
        var executor = request.f2lMode() == F2LMode.OPTIMIZED ? optimizedExecutor : fastExecutor;
        job.future = executor.submit(() -> run(
                job,
                request,
                normalizedCrossFace(apiRequest.crossFace()),
                userId,
                solveId,
                saveOnComplete
        ));
        return job.snapshot();
    }

    JobSnapshot find(String jobId) {
        return requireJob(jobId).snapshot();
    }

    JobSnapshot cancel(String jobId) {
        var job = requireJob(jobId);
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

    private void run(
            JobState job,
            solver.CfopSolveRequest request,
            String requestedCrossFace,
            String userId,
            Long solveId,
            boolean saveOnComplete
    ) {
        if (!job.markRunning()) {
            return;
        }
        try {
            var result = solveService.solveWithProgress(request, progress -> {
                SolveCancellation.throwIfCancelled();
                job.updateProgress(progress);
            });
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
                retainFinished(job);
            }
        } catch (SolveCancelledException exception) {
            if (job.cancel()) {
                retainFinished(job);
            }
        } catch (Exception exception) {
            if (Thread.currentThread().isInterrupted()) {
                if (job.cancel()) {
                    retainFinished(job);
                }
                return;
            }
            if (job.fail(exception)) {
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
                DatabaseManager.SOLVER_VERSION
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
            CfopSolveResult result,
            String error
    ) {
    }

    private static final class JobState {
        private final String id;
        private final String userId;
        private final Long solveId;
        private final boolean saveOnComplete;
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
        private volatile String status = "queued";
        private volatile CfopSolveResult result;
        private volatile String error;
        private volatile Future<?> future;
        private boolean retained;

        private JobState(String id, String userId, Long solveId, boolean saveOnComplete) {
            this.id = id;
            this.userId = userId;
            this.solveId = solveId;
            this.saveOnComplete = saveOnComplete;
        }

        private synchronized boolean markRunning() {
            if (!"queued".equals(status)) {
                return false;
            }
            status = "running";
            return true;
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
                    || "cancelled".equals(status);
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
