package solver;

import algorithms.OLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cube.Algorithm;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.OrientationFrames;
import cube.OrientedCube;

import java.util.List;

public class OLLSolver {
    private static final Algorithm[] PREFIX_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y)),
            Algorithm.fromMoves(List.of(Move.Y, Move.U)),
            Algorithm.fromMoves(List.of(Move.Y, Move.U2)),
            Algorithm.fromMoves(List.of(Move.Y, Move.U_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y2)),
            Algorithm.fromMoves(List.of(Move.Y2, Move.U)),
            Algorithm.fromMoves(List.of(Move.Y2, Move.U2)),
            Algorithm.fromMoves(List.of(Move.Y2, Move.U_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME, Move.U)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME, Move.U2)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME, Move.U_PRIME))
    };

    private final OLLCaseDatabase caseDatabase;

    public OLLSolver() {
        this(null);
    }

    public OLLSolver(OLLCaseDatabase caseDatabase) {
        this.caseDatabase = caseDatabase;
        if (this.caseDatabase != null) {
            this.caseDatabase.validate();
        }
    }

    public Algorithm solve(CubeState cube) {
        return solveStage(cube.copy(), new CubeOrientation());
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveStage(cube.copy(), OrientationFrames.orientedFrameFor(crossFace)));
    }

    public Algorithm solve(OrientedCube cube) {
        return solveStage(cube.cubeState().copy(), cube.orientation());
    }

    private Algorithm solveStage(CubeState cube, CubeOrientation orientation) {
        if (caseDatabase == null || caseDatabase.size() == 0) {
            throw new IllegalStateException("OLL solver requires a non-empty OLL case database");
        }

        ensurePreconditions(cube, orientation);
        if (OLLAnalyzer.isOllSolved(cube, orientation)) {
            return new Algorithm();
        }

        for (var prefix : PREFIX_TRIALS) {
            var trialCube = cube.copy();
            var resultingOrientation = executeAndReturnOrientation(trialCube, orientation, prefix.getMoves());
            var signature = OLLAnalyzer.extractSignature(trialCube, resultingOrientation);
            var match = caseDatabase.find(signature);
            if (match.isPresent()) {
                var candidate = prefix.concat(match.get().algorithm());
                if (solvesOll(cube, orientation, candidate)) {
                    return candidate;
                }
            }

            for (var ollCase : caseDatabase.allCases()) {
                if (match.isPresent() && ollCase == match.get()) {
                    continue;
                }
                var candidate = prefix.concat(ollCase.algorithm());
                if (solvesOll(cube, orientation, candidate)) {
                    return candidate;
                }
            }
        }

        throw new IllegalStateException("No OLL case match found for current last-layer orientation");
    }

    private static boolean solvesOll(CubeState cube, CubeOrientation orientation, Algorithm candidate) {
        var validationCube = cube.copy();
        var finalOrientation = executeAndReturnOrientation(validationCube, orientation, candidate.getMoves());
        return CrossAnalyzer.isCrossSolved(validationCube, finalOrientation)
                && F2LAnalyzer.isF2LSolved(validationCube, finalOrientation)
                && OLLAnalyzer.isOllSolved(validationCube, finalOrientation);
    }

    private static void ensurePreconditions(CubeState cube, CubeOrientation orientation) {
        if (!CrossAnalyzer.isCrossSolved(cube, orientation)) {
            throw new IllegalArgumentException("OLL requires a solved cross");
        }
        if (!F2LAnalyzer.isF2LSolved(cube, orientation)) {
            throw new IllegalArgumentException("OLL requires solved F2L");
        }
    }

    private static CubeOrientation executeAndReturnOrientation(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var orientedCube = new OrientedCube(cube, orientation);
        orientedCube.applyMoves(moves);
        return orientedCube.orientation();
    }

}
