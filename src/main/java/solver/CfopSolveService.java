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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public class CfopSolveService {
    private final F2LSetupCaseDatabase f2lSetupDatabase;
    private final F2LInsertCaseDatabase f2lInsertDatabase;
    private final OLLCaseDatabase ollDatabase;
    private final PLLCaseDatabase pllDatabase;
    private final OLLSolver ollSolver;
    private final PLLSolver pllSolver;

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
        this.ollSolver = this.ollDatabase.size() == 0 ? null : new OLLSolver(this.ollDatabase);
        this.pllSolver = this.pllDatabase.size() == 0 ? null : new PLLSolver(this.pllDatabase);
    }

    public CfopSolveResult solve(CfopSolveRequest request) {
        return solveWithProgress(request, ignored -> {
        });
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
        SolveCancellation.throwIfCancelled();
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (!ScrambleParser.isValid(request.scramble())) {
            throw new IllegalArgumentException("Invalid scramble notation");
        }

        long startTime = System.nanoTime();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, request.scramble());

        var orientedCube = new OrientedCube(cube);

        var crossSolve = solveCross(orientedCube.cubeState(), request);
        var crossFace = crossSolve.face();
        var crossSolution = crossSolve.algorithm();
        orientedCube.applyMoves(crossSolution.getMoves());
        var crossResult = new CfopStageResult(
                "cross",
                crossSolution.toString(),
                crossSolution.getMoveCount(),
                CrossAnalyzer.isCrossSolved(cube, crossFace),
                "ok"
        );

        var postCrossCube = orientedCube.cubeState().copy();
        var postCrossOrientation = orientedCube.orientation();
        var f2lSolver = new F2LSolver(f2lSetupDatabase, f2lInsertDatabase);
        var fastF2l = f2lSolver.solve(new OrientedCube(postCrossCube.copy(), postCrossOrientation));
        var continuation = evaluateContinuation(postCrossCube, postCrossOrientation, fastF2l, false);

        if (request.f2lMode() == F2LMode.OPTIMIZED) {
            var latestProgress = new AtomicReference<>(new F2LSolver.F2LSearchProgress(
                    0,
                    0,
                    0,
                    fastF2l.getMoveCount(),
                    0,
                    0,
                    crossResult.moveCount() + continuation.totalMoves()
            ));
            var candidates = f2lSolver.solveOptimizedCandidates(
                    new OrientedCube(postCrossCube.copy(), postCrossOrientation),
                    progress -> {
                        latestProgress.set(progress);
                        optimizedProgressListener.accept(progress);
                    }
            );
            int evaluated = 0;
            for (var candidate : candidates) {
                SolveCancellation.throwIfCancelled();
                var evaluatedContinuation = evaluateContinuation(
                        postCrossCube,
                        postCrossOrientation,
                        candidate.algorithm(),
                        true
                );
                evaluated++;
                if (CONTINUATION_COMPARATOR.compare(evaluatedContinuation, continuation) < 0) {
                    continuation = evaluatedContinuation;
                }
                var progress = latestProgress.get();
                optimizedProgressListener.accept(new F2LSolver.F2LSearchProgress(
                        progress.statesExplored(),
                        progress.statesPruned(),
                        progress.duplicateStates(),
                        progress.bestMoves(),
                        progress.completedCandidates(),
                        evaluated,
                        crossResult.moveCount() + continuation.totalMoves()
                ));
            }
        }

        return new CfopSolveResult(
                request.scramble(),
                crossFace.toString(),
                request.f2lMode().apiValue(),
                f2lSetupDatabase.size(),
                f2lInsertDatabase.size(),
                crossResult,
                continuation.f2l(),
                continuation.oll(),
                continuation.pll(),
                solvedSlotSummary(continuation.cube(), crossFace),
                continuation.fullySolved(),
                (System.nanoTime() - startTime) / 1_000_000.0
        );
    }

    private CrossSolution solveCross(CubeState cube, CfopSolveRequest request) {
        var crossSolver = new CrossSolver();
        if (request.colorNeutralCross()) {
            return crossSolver.solveColorNeutral(cube);
        }
        return new CrossSolution(request.crossFace(), crossSolver.solve(cube, request.crossFace()));
    }

    private CfopStageResult solveOll(CubeState cube, OrientedCube orientedCube) {
        if (ollDatabase.size() == 0) {
            return new CfopStageResult("oll", "", 0, false, "skipped (no seeded OLL cases)");
        }

        try {
            var ollSolution = ollSolver.solve(orientedCube);
            orientedCube.applyMoves(ollSolution.getMoves());
            return new CfopStageResult(
                    "oll",
                    ollSolution.toString(),
                    ollSolution.getMoveCount(),
                    OLLAnalyzer.isOllSolved(cube, orientedCube.orientation()),
                    "ok"
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new CfopStageResult("oll", "", 0, false, "not solved (" + exception.getMessage() + ")");
        }
    }

    private CfopStageResult solvePll(CubeState cube, OrientedCube orientedCube) {
        if (pllDatabase.size() == 0) {
            return new CfopStageResult("pll", "", 0, false, "skipped (no seeded PLL cases)");
        }
        if (!OLLAnalyzer.isOllSolved(cube, orientedCube.orientation())) {
            return new CfopStageResult("pll", "", 0, false, "skipped (OLL not solved)");
        }

        try {
            var pllSolution = pllSolver.solve(orientedCube);
            orientedCube.applyMoves(pllSolution.getMoves());
            return new CfopStageResult(
                    "pll",
                    pllSolution.toString(),
                    pllSolution.getMoveCount(),
                    PLLAnalyzer.isPllSolved(cube, orientedCube.orientation()),
                    "ok"
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new CfopStageResult("pll", "", 0, false, "not solved (" + exception.getMessage() + ")");
        }
    }

    private Continuation evaluateContinuation(
            CubeState postCrossCube,
            CubeOrientation postCrossOrientation,
            Algorithm f2lAlgorithm,
            boolean optimizeLastLayer
    ) {
        var candidateCube = new OrientedCube(postCrossCube.copy(), postCrossOrientation);
        candidateCube.applyMoves(f2lAlgorithm.getMoves());
        var f2lResult = new CfopStageResult(
                "f2l",
                f2lAlgorithm.toString(),
                f2lAlgorithm.getMoveCount(),
                F2LAnalyzer.isF2LSolved(candidateCube.cubeState(), candidateCube.orientation()),
                "ok"
        );

        if (optimizeLastLayer && ollDatabase.size() > 0) {
            try {
                Continuation best = null;
                for (var ollAlgorithm : ollSolver.solveCandidates(candidateCube)) {
                    SolveCancellation.throwIfCancelled();
                    var lastLayerCube = new OrientedCube(
                            candidateCube.cubeState().copy(),
                            candidateCube.orientation()
                    );
                    lastLayerCube.applyMoves(ollAlgorithm.getMoves());
                    var ollResult = new CfopStageResult(
                            "oll",
                            ollAlgorithm.toString(),
                            ollAlgorithm.getMoveCount(),
                            OLLAnalyzer.isOllSolved(lastLayerCube.cubeState(), lastLayerCube.orientation()),
                            "ok"
                    );
                    var pllResult = solvePll(lastLayerCube.cubeState(), lastLayerCube);
                    var evaluated = new Continuation(
                            f2lResult,
                            ollResult,
                            pllResult,
                            lastLayerCube.cubeState().copy(),
                            isFullySolved(lastLayerCube.cubeState(), lastLayerCube)
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

        var ollResult = solveOll(candidateCube.cubeState(), candidateCube);
        var pllResult = solvePll(candidateCube.cubeState(), candidateCube);
        return new Continuation(
                f2lResult,
                ollResult,
                pllResult,
                candidateCube.cubeState().copy(),
                isFullySolved(candidateCube.cubeState(), candidateCube)
        );
    }

    private static boolean isFullySolved(CubeState cube, OrientedCube orientedCube) {
        return CrossAnalyzer.isCrossSolved(cube, orientedCube.orientation())
                && F2LAnalyzer.isF2LSolved(cube, orientedCube.orientation())
                && OLLAnalyzer.isOllSolved(cube, orientedCube.orientation())
                && PLLAnalyzer.isPllSolved(cube, orientedCube.orientation());
    }

    private static String solvedSlotSummary(CubeState cube, Face crossFace) {
        var solved = new ArrayList<String>();
        for (var slot : F2LAnalyzer.getSolvedSlots(cube, crossFace)) {
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

    private record Continuation(
            CfopStageResult f2l,
            CfopStageResult oll,
            CfopStageResult pll,
            CubeState cube,
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
}
