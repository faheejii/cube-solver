package solver;

import algorithms.F2LInsertCaseDatabase;
import algorithms.F2LSetupCaseDatabase;
import algorithms.OLLCaseDatabase;
import algorithms.PLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.Algorithm;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import io.ScrambleParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class CfopSolveService {
    private static final Face[] COLOR_NEUTRAL_FACES = {Face.U, Face.D, Face.F, Face.B, Face.L, Face.R};
    private static final int COLOR_NEUTRAL_BASELINE_CANDIDATES = 3;
    private static final int COLOR_NEUTRAL_OPTIMIZED_CANDIDATES = 2;
    private static final long COLOR_NEUTRAL_BASELINE_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(12);
    private static final long DEFAULT_OPTIMIZATION_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(15);
    private final F2LSetupCaseDatabase f2lSetupDatabase;
    private final F2LInsertCaseDatabase f2lInsertDatabase;
    private final OLLCaseDatabase ollDatabase;
    private final PLLCaseDatabase pllDatabase;
    private final F2LSolver f2lSolver;
    private final LastLayerSolver lastLayerSolver;

    public CfopSolveService() {
        this(
                F2LSetupCaseDatabase.seedCases(),
                F2LInsertCaseDatabase.seedCases(),
                OLLCaseDatabase.seedCases(),
                PLLCaseDatabase.seedCases()
        );
    }

    public CfopSolveService(
            F2LSetupCaseDatabase f2lSetupDatabase,
            F2LInsertCaseDatabase f2lInsertDatabase,
            OLLCaseDatabase ollDatabase,
            PLLCaseDatabase pllDatabase
    ) {
        this.f2lSetupDatabase = f2lSetupDatabase == null ? F2LSetupCaseDatabase.empty() : f2lSetupDatabase;
        this.f2lInsertDatabase = f2lInsertDatabase == null ? F2LInsertCaseDatabase.empty() : f2lInsertDatabase;
        this.ollDatabase = ollDatabase == null ? OLLCaseDatabase.empty() : ollDatabase;
        this.pllDatabase = pllDatabase == null ? PLLCaseDatabase.empty() : pllDatabase;
        this.f2lSolver = new F2LSolver(this.f2lSetupDatabase, this.f2lInsertDatabase);
        this.lastLayerSolver = new LastLayerSolver(this.ollDatabase, this.pllDatabase);
    }

    public CfopSolveResult solve(CfopSolveRequest request) {
        return solveWithProgress(request, ignored -> {
        });
    }

    public F2LSolver.F2LDiagnostics f2lDiagnostics() {
        return f2lSolver.diagnostics();
    }

    public CfopSolveResult solve(CfopSolveRequest request, LongConsumer optimizedProgressListener) {
        return solveWithProgress(
                request,
                progress -> optimizedProgressListener.accept(progress.statesExplored())
        );
    }

    public CfopSolveResult solveWithProgress(
            CfopSolveRequest request,
            Consumer<F2LSolver.F2LSearchProgress> optimizedProgressListener
    ) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (!ScrambleParser.isValid(request.scramble())) {
            throw new IllegalArgumentException("Invalid scramble notation");
        }

        SolveCancellation.throwIfCancelled();
        if (request.colorNeutralCross()) {
            return solveColorNeutral(request, optimizedProgressListener);
        }
        return solveFixedCross(request, optimizedProgressListener);
    }

    private CfopSolveResult solveColorNeutral(
            CfopSolveRequest request,
            Consumer<F2LSolver.F2LSearchProgress> optimizedProgressListener
    ) {
        if (request.f2lMode() == F2LMode.OPTIMIZED) {
            return solveColorNeutralOptimized(request, optimizedProgressListener);
        }

        var scrambledCube = new CubeState();
        MoveApplier.applyAlgorithm(scrambledCube, request.scramble());
        var crossSolver = new CrossSolver();
        var crossCandidates = new ArrayList<CrossCandidate>();
        for (var face : COLOR_NEUTRAL_FACES) {
            SolveCancellation.throwIfCancelled();
            crossCandidates.add(new CrossCandidate(face, crossSolver.solve(scrambledCube, face)));
        }
        crossCandidates.sort(Comparator
                .comparingInt((CrossCandidate candidate) -> candidate.algorithm().getMoveCount())
                .thenComparingInt(candidate -> candidate.face().ordinal()));

        var shortlisted = shortlistCrossCandidates(crossCandidates);
        var baselineDeadlineNanos = System.nanoTime() + COLOR_NEUTRAL_BASELINE_BUDGET_NANOS;
        FixedCrossBaseline best = null;
        for (var crossCandidate : shortlisted) {
            SolveCancellation.throwIfCancelled();
            try {
                var baseline = SolveCancellation.withStopSignal(
                        () -> System.nanoTime() >= baselineDeadlineNanos,
                        () -> prepareFixedCross(request.scramble(), crossCandidate)
                );
                if (best == null || SOLUTION_COMPARATOR.compare(
                        resultFor(baseline, baseline.continuation(), F2LMode.GREEDY, 0),
                        resultFor(best, best.continuation(), F2LMode.GREEDY, 0)
                ) < 0) {
                    best = baseline;
                }
            } catch (SolveBudgetExceededException ignored) {
                break;
            }
        }

        if (best == null) {
            var fallback = shortlisted.get(0);
            best = prepareFixedCross(request.scramble(), fallback);
        }
        return resultFor(best, best.continuation(), F2LMode.GREEDY,
                System.nanoTime() - best.startTimeNanos());
    }

    private CfopSolveResult solveColorNeutralOptimized(
            CfopSolveRequest request,
            Consumer<F2LSolver.F2LSearchProgress> progressListener
    ) {
        long startTime = System.nanoTime();
        var scrambledCube = new CubeState();
        MoveApplier.applyAlgorithm(scrambledCube, request.scramble());
        var crossSolver = new CrossSolver();
        var crossCandidates = new ArrayList<CrossCandidate>();
        for (int index = 0; index < COLOR_NEUTRAL_FACES.length; index++) {
            var face = COLOR_NEUTRAL_FACES[index];
            publishProgress(progressListener, F2LSolver.SolvePhase.CROSS_EVALUATION, face,
                    index, COLOR_NEUTRAL_FACES.length, 0, 0, false, null);
            crossCandidates.add(new CrossCandidate(face, crossSolver.solve(scrambledCube, face)));
            publishProgress(progressListener, F2LSolver.SolvePhase.CROSS_EVALUATION, face,
                    index + 1, COLOR_NEUTRAL_FACES.length, 0, 0, false, null);
        }
        crossCandidates.sort(Comparator
                .comparingInt((CrossCandidate candidate) -> candidate.algorithm().getMoveCount())
                .thenComparingInt(candidate -> candidate.face().ordinal()));
        var shortlisted = shortlistCrossCandidates(crossCandidates);
        var baselines = new ArrayList<FixedCrossBaseline>();
        long baselineDeadlineNanos = System.nanoTime() + COLOR_NEUTRAL_BASELINE_BUDGET_NANOS;
        for (int index = 0; index < shortlisted.size(); index++) {
            var crossCandidate = shortlisted.get(index);
            var face = crossCandidate.face();
            long currentDeadlineNanos = baselineDeadlineNanos;
            publishProgress(progressListener, F2LSolver.SolvePhase.BASELINE_COMPARISON, face,
                    index, shortlisted.size(), 0, 0, false, null);
            try {
                var baseline = SolveCancellation.withStopSignal(
                        () -> System.nanoTime() >= currentDeadlineNanos,
                        () -> prepareFixedCross(request.scramble(), crossCandidate)
                );
                baselines.add(baseline);
                publishProgress(progressListener, F2LSolver.SolvePhase.BASELINE_COMPARISON, face,
                        index + 1, shortlisted.size(), 0, 0, false, null);
            } catch (SolveBudgetExceededException ignored) {
                publishProgress(progressListener, F2LSolver.SolvePhase.BASELINE_BUDGET_REACHED, face,
                        baselines.size(), shortlisted.size(), 0, 0, false, null);
                break;
            }
        }
        if (baselines.isEmpty()) {
            var fallback = shortlisted.get(0);
            publishProgress(progressListener, F2LSolver.SolvePhase.BASELINE_FALLBACK, fallback.face(),
                    0, shortlisted.size(), 0, 0, false, null);
            baselines.add(prepareFixedCross(request.scramble(), fallback));
        }
        baselines.sort(Comparator.comparing(
                baseline -> resultFor(baseline, baseline.continuation(), F2LMode.OPTIMIZED, 0),
                SOLUTION_COMPARATOR
        ));

        var best = baselines.get(0);
        var deadlineNanos = optimizationDeadlineNanos();
        int candidateCount = Math.min(COLOR_NEUTRAL_OPTIMIZED_CANDIDATES, baselines.size());
        boolean budgetExpired = false;
        for (int index = 0; index < candidateCount; index++) {
            if (System.nanoTime() >= deadlineNanos) {
                budgetExpired = true;
                break;
            }
            var baseline = baselines.get(index);
            int rank = index + 1;
            var face = baseline.crossFace();
            var searchLimitReached = new AtomicBoolean();
            publishProgress(progressListener, F2LSolver.SolvePhase.F2L_OPTIMIZATION, face,
                    baselines.size(), shortlisted.size(), rank, candidateCount, false, null);
            var optimizedContinuation = optimizeContinuation(
                    baseline,
                    progress -> {
                        searchLimitReached.compareAndSet(false, progress.searchLimitReached());
                        publishProgress(progressListener, F2LSolver.SolvePhase.F2L_CANDIDATE_GENERATION,
                                face, baselines.size(), shortlisted.size(),
                                rank, candidateCount, false, progress);
                    },
                    () -> System.nanoTime() >= deadlineNanos
            );
            var optimized = baseline.withContinuation(optimizedContinuation);
            if (SOLUTION_COMPARATOR.compare(
                    resultFor(optimized, optimized.continuation(), F2LMode.OPTIMIZED, 0),
                    resultFor(best, best.continuation(), F2LMode.OPTIMIZED, 0)
            ) < 0) {
                best = optimized;
            }
            if (searchLimitReached.get() || System.nanoTime() >= deadlineNanos) {
                budgetExpired = true;
                break;
            }
        }

        publishProgress(progressListener,
                budgetExpired ? F2LSolver.SolvePhase.OPTIMIZATION_BUDGET_REACHED : F2LSolver.SolvePhase.COMPLETE,
                best.crossFace(), baselines.size(), shortlisted.size(),
                candidateCount, candidateCount, budgetExpired, null);
        return resultFor(best, best.continuation(), F2LMode.OPTIMIZED, System.nanoTime() - startTime);
    }

    private CfopSolveResult solveFixedCross(
            CfopSolveRequest request,
            Consumer<F2LSolver.F2LSearchProgress> optimizedProgressListener
    ) {
        return solveFixedCross(request, optimizedProgressListener, () -> false);
    }

    private CfopSolveResult solveFixedCross(
            CfopSolveRequest request,
            Consumer<F2LSolver.F2LSearchProgress> optimizedProgressListener,
            BooleanSupplier shouldStop
    ) {
        var baseline = prepareFixedCross(request);
        var continuation = baseline.continuation();
        if (request.f2lMode() == F2LMode.OPTIMIZED) {
            long deadlineNanos = optimizationDeadlineNanos();
            var searchLimitReached = new AtomicBoolean();
            var boundedStop = (BooleanSupplier) () -> shouldStop.getAsBoolean()
                    || System.nanoTime() >= deadlineNanos;
            continuation = optimizeContinuation(baseline, progress -> {
                searchLimitReached.compareAndSet(false, progress.searchLimitReached());
                optimizedProgressListener.accept(progress);
            }, boundedStop);
            if (searchLimitReached.get() || System.nanoTime() >= deadlineNanos) {
                publishProgress(optimizedProgressListener, F2LSolver.SolvePhase.OPTIMIZATION_BUDGET_REACHED,
                        baseline.crossFace(), 1, 1, 1, 1, true, null);
            }
        }
        return resultFor(baseline, continuation, request.f2lMode(), System.nanoTime() - baseline.startTimeNanos());
    }

    private static java.util.List<CrossCandidate> shortlistCrossCandidates(ArrayList<CrossCandidate> candidates) {
        return java.util.List.copyOf(candidates.subList(
                0,
                Math.min(COLOR_NEUTRAL_BASELINE_CANDIDATES, candidates.size())
        ));
    }

    /**
     * Production keeps a finite optimization budget. Developers may opt into
     * a longer diagnostic run with -Df2l.optimization.budget-seconds=60. An
     * unlimited run is accepted only when f2l.diagnostic is also enabled.
     */
    private static long optimizationDeadlineNanos() {
        var configured = System.getProperty("f2l.optimization.budget-seconds");
        if (configured == null || configured.isBlank()) {
            return System.nanoTime() + DEFAULT_OPTIMIZATION_BUDGET_NANOS;
        }
        try {
            var seconds = Long.parseLong(configured.trim());
            if (seconds < 0) {
                if (Boolean.getBoolean("f2l.diagnostic")) {
                    return Long.MAX_VALUE;
                }
                return System.nanoTime() + DEFAULT_OPTIMIZATION_BUDGET_NANOS;
            }
            var nanos = TimeUnit.SECONDS.toNanos(seconds);
            return System.nanoTime() + nanos;
        } catch (NumberFormatException ignored) {
            return System.nanoTime() + DEFAULT_OPTIMIZATION_BUDGET_NANOS;
        }
    }

    private FixedCrossBaseline prepareFixedCross(CfopSolveRequest request) {
        long startTime = System.nanoTime();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, request.scramble());
        return prepareFixedCross(request.scramble(), new CrossCandidate(
                request.crossFace(), new CrossSolver().solve(cube, request.crossFace())
        ), startTime);
    }

    private FixedCrossBaseline prepareFixedCross(String scramble, CrossCandidate crossCandidate) {
        return prepareFixedCross(scramble, crossCandidate, System.nanoTime());
    }

    private FixedCrossBaseline prepareFixedCross(
            String scramble,
            CrossCandidate crossCandidate,
            long startTime
    ) {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);

        var crossFace = crossCandidate.face();
        var crossSolution = crossCandidate.algorithm();
        // Execute the public algorithm as one frame-aware sequence. This
        // preserves its visible rotation prefix and makes the orientation
        // consumed by F2L exactly the orientation the user sees.
        var orientedCube = new OrientedCube(cube);
        orientedCube.applyMoves(crossSolution.getMoves());
        var crossResult = new CfopStageResult(
                "cross",
                crossSolution.toString(),
                crossSolution.getMoveCount(),
                CrossAnalyzer.isCrossSolved(cube, orientedCube.orientation()),
                "ok"
        );

        var postCrossCube = orientedCube.cubeState().copy();
        var postCrossOrientation = orientedCube.orientation();
        var fastF2lTrace = f2lSolver.solveTrace(
                new OrientedCube(postCrossCube.copy(), postCrossOrientation)
        );
        var fastF2l = fastF2lTrace.algorithm();
        var continuation = evaluateContinuation(postCrossCube, postCrossOrientation, fastF2lTrace, false);

        return new FixedCrossBaseline(
                startTime, scramble, crossFace, crossResult, postCrossCube, postCrossOrientation,
                fastF2l, continuation
        );
    }

    private Continuation optimizeContinuation(
            FixedCrossBaseline baseline,
            Consumer<F2LSolver.F2LSearchProgress> optimizedProgressListener,
            BooleanSupplier shouldStop
    ) {
        var continuation = baseline.continuation();
        var latestProgress = new AtomicReference<>(new F2LSolver.F2LSearchProgress(
                0, 0, 0, baseline.fastF2l().getMoveCount(), 0, 0,
                baseline.crossResult().moveCount() + continuation.totalMoves()
        ));
        var candidates = f2lSolver.solveOptimizedCandidates(
                new OrientedCube(baseline.postCrossCube().copy(), baseline.postCrossOrientation()),
                progress -> {
                    latestProgress.set(progress);
                    optimizedProgressListener.accept(progress);
                }, baseline.fastF2l(), shouldStop
        );
        int evaluated = 0;
        for (var candidate : candidates) {
            if (shouldStop.getAsBoolean()) {
                break;
            }
            SolveCancellation.throwIfCancelled();
            var evaluatedContinuation = evaluateContinuation(candidate, true);
            evaluated++;
            if (CONTINUATION_COMPARATOR.compare(evaluatedContinuation, continuation) < 0) {
                continuation = evaluatedContinuation;
            }
            var progress = latestProgress.get();
            optimizedProgressListener.accept(new F2LSolver.F2LSearchProgress(
                    progress.statesExplored(), progress.statesPruned(), progress.duplicateStates(),
                    progress.bestMoves(), progress.completedCandidates(), evaluated,
                    baseline.crossResult().moveCount() + continuation.totalMoves()
            ));
        }
        return continuation;
    }

    private CfopSolveResult resultFor(
            FixedCrossBaseline baseline,
            Continuation continuation,
            F2LMode f2lMode,
            long elapsedNanos
    ) {
        var modeComparison = f2lMode == F2LMode.OPTIMIZED
                ? F2LModeComparison.between(
                        F2LModeSummary.from(
                                baseline.crossFace().toString(),
                                baseline.continuation().f2lTrace(),
                                baseline.continuation().oll(),
                                baseline.continuation().pll()
                        ),
                        F2LModeSummary.from(
                                baseline.crossFace().toString(),
                                continuation.f2lTrace(),
                                continuation.oll(),
                                continuation.pll()
                        )
                )
                : null;
        return new CfopSolveResult(
                baseline.scramble(),
                baseline.crossFace().toString(),
                f2lMode.apiValue(),
                f2lSetupDatabase.size(),
                f2lInsertDatabase.size(),
                baseline.crossResult(),
                continuation.f2l(),
                continuation.oll(),
                continuation.pll(),
                solvedSlotSummary(continuation.cube(), continuation.orientation()),
                continuation.fullySolved(),
                elapsedNanos / 1_000_000.0,
                modeComparison,
                continuation.f2lTrace()
        );
    }

    private static void publishProgress(
            Consumer<F2LSolver.F2LSearchProgress> listener,
            F2LSolver.SolvePhase phase,
            Face crossFace,
            int completedCrosses,
            int totalCrosses,
            int optimizationCandidate,
            int totalOptimizationCandidates,
            boolean optimizationBudgetExpired,
            F2LSolver.F2LSearchProgress base
    ) {
        var progress = base == null ? new F2LSolver.F2LSearchProgress(0, 0, 0, -1, 0, 0, -1) : base;
        listener.accept(progress.withMetadata(
                phase, crossFace == null ? "" : crossFace.toString(), completedCrosses, totalCrosses,
                optimizationCandidate, totalOptimizationCandidates, optimizationBudgetExpired
        ));
    }

    private Continuation evaluateContinuation(
            CubeState postCrossCube,
            CubeOrientation postCrossOrientation,
            F2LSolveTrace f2lTrace,
            boolean optimizeLastLayer
    ) {
        var candidateCube = new OrientedCube(postCrossCube.copy(), postCrossOrientation);
        candidateCube.applyMoves(f2lTrace.algorithmMoves());
        return evaluateContinuation(candidateCube, f2lTrace, optimizeLastLayer);
    }

    private Continuation evaluateContinuation(
            F2LSolver.F2LCandidate candidate,
            boolean optimizeLastLayer
    ) {
        return evaluateContinuation(
                new OrientedCube(candidate.cube().copy(), candidate.orientation()),
                candidate.trace(),
                optimizeLastLayer
        );
    }

    private Continuation evaluateContinuation(
            OrientedCube candidateCube,
            F2LSolveTrace f2lTrace,
            boolean optimizeLastLayer
    ) {
        var f2lAlgorithm = f2lTrace.algorithm();
        var f2lResult = new CfopStageResult(
                "f2l",
                f2lAlgorithm.toString(),
                f2lAlgorithm.getMoveCount(),
                F2LAnalyzer.isF2LSolved(candidateCube.cubeState(), candidateCube.orientation()),
                "ok"
        );
        var lastLayerCube = new OrientedCube(candidateCube.cubeState().copy(), candidateCube.orientation());

        if (optimizeLastLayer && ollDatabase.size() > 0) {
            try {
                Continuation best = null;
                for (var ollAlgorithm : lastLayerSolver.ollCandidates(lastLayerCube)) {
                    SolveCancellation.throwIfCancelled();
                    var solvedLastLayerCube = new OrientedCube(
                            lastLayerCube.cubeState().copy(),
                            lastLayerCube.orientation()
                    );
                    solvedLastLayerCube.applyMoves(ollAlgorithm.getMoves());
                    var ollResult = new CfopStageResult(
                        "oll",
                            ollAlgorithm.toString(),
                            ollAlgorithm.getMoveCount(),
                            OLLAnalyzer.isOllSolved(solvedLastLayerCube.cubeState(), solvedLastLayerCube.orientation()),
                            "ok"
                    );
                    var pllResult = lastLayerSolver.solvePll(solvedLastLayerCube.cubeState(), solvedLastLayerCube);
                    var evaluated = new Continuation(
                            f2lResult,
                            f2lTrace,
                            ollResult,
                            pllResult,
                            solvedLastLayerCube.cubeState().copy(),
                            solvedLastLayerCube.orientation(),
                            isFullySolved(solvedLastLayerCube.cubeState(), solvedLastLayerCube)
                    );
                    if (best == null || CONTINUATION_COMPARATOR.compare(evaluated, best) < 0) {
                        best = evaluated;
                    }
                }
                if (best != null) {
                    return best;
                }
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Use the normal stage path so failure status remains visible in the result.
            }
        }

        var ollResult = lastLayerSolver.solveOll(lastLayerCube.cubeState(), lastLayerCube);
        var pllResult = lastLayerSolver.solvePll(lastLayerCube.cubeState(), lastLayerCube);
        return new Continuation(
                f2lResult,
                f2lTrace,
                ollResult,
                pllResult,
                lastLayerCube.cubeState().copy(),
                lastLayerCube.orientation(),
                isFullySolved(lastLayerCube.cubeState(), lastLayerCube)
        );
    }

    private static boolean isFullySolved(CubeState cube, OrientedCube orientedCube) {
        return CrossAnalyzer.isCrossSolved(cube, orientedCube.orientation())
                && F2LAnalyzer.isF2LSolved(cube, orientedCube.orientation())
                && OLLAnalyzer.isOllSolved(cube, orientedCube.orientation())
                && PLLAnalyzer.isPllSolved(cube, orientedCube.orientation());
    }

    private static String solvedSlotSummary(CubeState cube, CubeOrientation orientation) {
        var solved = new ArrayList<String>();
        for (var slot : F2LAnalyzer.getSolvedSlots(cube, orientation)) {
            solved.add(slot.name());
        }
        return solved.toString();
    }

    private static final Comparator<Continuation> CONTINUATION_COMPARATOR = Comparator
            .comparing(Continuation::fullySolved).reversed()
            .thenComparingInt(Continuation::totalMoves)
            .thenComparingInt(continuation -> continuation.f2l().moveCount())
            .thenComparingInt(Continuation::rawMoves)
            .thenComparing(Continuation::algorithmText);

    private static final Comparator<CfopSolveResult> SOLUTION_COMPARATOR = Comparator
            .comparing(CfopSolveResult::fullySolved).reversed()
            .thenComparingInt(CfopSolveResult::totalMoveCount)
            .thenComparing(result -> result.cross().moveCount())
            .thenComparing(result -> result.cross().algorithm() + "|" + result.f2l().algorithm()
                    + "|" + result.oll().algorithm() + "|" + result.pll().algorithm());

    private record Continuation(
            CfopStageResult f2l,
            F2LSolveTrace f2lTrace,
            CfopStageResult oll,
            CfopStageResult pll,
            CubeState cube,
            CubeOrientation orientation,
            boolean fullySolved
    ) {
        private int totalMoves() {
            return f2l.moveCount() + oll.moveCount() + pll.moveCount();
        }

        private int rawMoves() {
            return rawMoveCount(f2l.algorithm())
                    + rawMoveCount(oll.algorithm())
                    + rawMoveCount(pll.algorithm());
        }

        private String algorithmText() {
            return f2l.algorithm() + "|" + oll.algorithm() + "|" + pll.algorithm();
        }

        private static int rawMoveCount(String algorithm) {
            return algorithm == null || algorithm.isBlank()
                    ? 0
                    : Algorithm.parse(algorithm).getMoves().size();
        }
    }

    private record FixedCrossBaseline(
            long startTimeNanos,
            String scramble,
            Face crossFace,
            CfopStageResult crossResult,
            CubeState postCrossCube,
            CubeOrientation postCrossOrientation,
            Algorithm fastF2l,
            Continuation continuation
    ) {
        private FixedCrossBaseline withContinuation(Continuation continuation) {
            return new FixedCrossBaseline(
                    startTimeNanos, scramble, crossFace, crossResult, postCrossCube, postCrossOrientation,
                    fastF2l, continuation
            );
        }
    }

    private record CrossCandidate(Face face, Algorithm algorithm) {
    }
}
