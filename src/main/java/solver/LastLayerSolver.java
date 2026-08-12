package solver;

import algorithms.OLLCaseDatabase;
import algorithms.PLLCaseDatabase;
import cube.Algorithm;
import cube.CubeState;
import cube.OrientedCube;

import java.util.List;

/** Coordinates OLL and PLL execution and keeps last-layer failure status consistent. */
final class LastLayerSolver {
    private final OLLCaseDatabase ollDatabase;
    private final PLLCaseDatabase pllDatabase;
    private final OLLSolver ollSolver;
    private final PLLSolver pllSolver;

    LastLayerSolver(OLLCaseDatabase ollDatabase, PLLCaseDatabase pllDatabase) {
        this.ollDatabase = ollDatabase;
        this.pllDatabase = pllDatabase;
        this.ollSolver = ollDatabase.size() == 0 ? null : new OLLSolver(ollDatabase);
        this.pllSolver = pllDatabase.size() == 0 ? null : new PLLSolver(pllDatabase);
    }

    List<Algorithm> ollCandidates(OrientedCube cube) {
        if (ollSolver == null) {
            return List.of();
        }
        return ollSolver.solveCandidates(cube);
    }

    CfopStageResult solveOll(CubeState cube, OrientedCube orientedCube) {
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
                    cfop.OLLAnalyzer.isOllSolved(cube, orientedCube.orientation()),
                    "ok"
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new CfopStageResult("oll", "", 0, false, "not solved (" + exception.getMessage() + ")");
        }
    }

    CfopStageResult solvePll(CubeState cube, OrientedCube orientedCube) {
        if (pllDatabase.size() == 0) {
            return new CfopStageResult("pll", "", 0, false, "skipped (no seeded PLL cases)");
        }
        if (!cfop.OLLAnalyzer.isOllSolved(cube, orientedCube.orientation())) {
            return new CfopStageResult("pll", "", 0, false, "skipped (OLL not solved)");
        }

        try {
            var pllSolution = pllSolver.solve(orientedCube);
            orientedCube.applyMoves(pllSolution.getMoves());
            return new CfopStageResult(
                    "pll",
                    pllSolution.toString(),
                    pllSolution.getMoveCount(),
                    cfop.PLLAnalyzer.isPllSolved(cube, orientedCube.orientation()),
                    "ok"
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new CfopStageResult("pll", "", 0, false, "not solved (" + exception.getMessage() + ")");
        }
    }
}
