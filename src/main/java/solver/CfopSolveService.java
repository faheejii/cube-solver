package solver;

import algorithms.F2LInsertCaseDatabase;
import algorithms.F2LSetupCaseDatabase;
import algorithms.OLLCaseDatabase;
import algorithms.PLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import io.ScrambleParser;

import java.util.ArrayList;

public class CfopSolveService {
    private final F2LSetupCaseDatabase f2lSetupDatabase;
    private final F2LInsertCaseDatabase f2lInsertDatabase;
    private final OLLCaseDatabase ollDatabase;
    private final PLLCaseDatabase pllDatabase;

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
    }

    public CfopSolveResult solve(CfopSolveRequest request) {
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

        var f2lSolver = new F2LSolver(f2lSetupDatabase, f2lInsertDatabase);
        var f2lSolution = switch (request.f2lMode()) {
            case GREEDY -> f2lSolver.solve(orientedCube);
            case OPTIMIZED -> f2lSolver.solveOptimized(orientedCube);
        };
        orientedCube.applyMoves(f2lSolution.getMoves());
        var f2lResult = new CfopStageResult(
                "f2l",
                f2lSolution.toString(),
                f2lSolution.getMoveCount(),
                F2LAnalyzer.isF2LSolved(cube, orientedCube.orientation()),
                "ok"
        );

        var ollSolve = solveOll(cube, orientedCube);
        var pllSolve = solvePll(cube, orientedCube);

        return new CfopSolveResult(
                request.scramble(),
                crossFace.toString(),
                request.f2lMode().apiValue(),
                f2lSetupDatabase.size(),
                f2lInsertDatabase.size(),
                crossResult,
                f2lResult,
                ollSolve,
                pllSolve,
                solvedSlotSummary(cube, crossFace),
                isFullySolved(cube, orientedCube),
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
            var ollSolution = new OLLSolver(ollDatabase).solve(orientedCube);
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
            var pllSolution = new PLLSolver(pllDatabase).solve(orientedCube);
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
}
