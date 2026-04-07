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
    private static final Algorithm[] AUF_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME))
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
        return solveStage(copyCube(cube), new CubeOrientation());
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveStage(copyCube(cube), OrientationFrames.orientedFrameFor(crossFace)));
    }

    public Algorithm solve(OrientedCube cube) {
        return solveStage(copyCube(cube.cubeState()), cube.orientation());
    }

    public Algorithm solveAfterF2L(CubeState cube, Face crossFace) {
        return solve(cube, crossFace);
    }

    private Algorithm solveStage(CubeState cube, CubeOrientation orientation) {
        if (caseDatabase == null || caseDatabase.size() == 0) {
            throw new IllegalStateException("OLL solver requires a non-empty OLL case database");
        }

        ensurePreconditions(cube, orientation);
        if (OLLAnalyzer.isOllSolved(cube, orientation)) {
            return new Algorithm();
        }

        for (var auf : AUF_TRIALS) {
            var trialCube = copyCube(cube);
            var resultingOrientation = executeAndReturnOrientation(trialCube, orientation, auf.getMoves());
            var signature = OLLAnalyzer.extractSignature(trialCube, resultingOrientation);
            var match = caseDatabase.find(signature);
            if (match.isEmpty()) {
                continue;
            }

            var candidate = auf.concat(match.get().algorithm());
            var validationCube = copyCube(cube);
            var finalOrientation = executeAndReturnOrientation(validationCube, orientation, candidate.getMoves());
            if (CrossAnalyzer.isCrossSolved(validationCube, finalOrientation)
                    && F2LAnalyzer.isF2LSolved(validationCube, finalOrientation)
                    && OLLAnalyzer.isOllSolved(validationCube, finalOrientation)) {
                return candidate;
            }
        }

        throw new IllegalStateException("No OLL case match found for current last-layer orientation");
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

    private static CubeState copyCube(CubeState cube) {
        var copy = new CubeState();
        copy.cornerPerm = java.util.Arrays.copyOf(cube.cornerPerm, cube.cornerPerm.length);
        copy.cornerOri = java.util.Arrays.copyOf(cube.cornerOri, cube.cornerOri.length);
        copy.edgePerm = java.util.Arrays.copyOf(cube.edgePerm, cube.edgePerm.length);
        copy.edgeOri = java.util.Arrays.copyOf(cube.edgeOri, cube.edgeOri.length);
        return copy;
    }
}
