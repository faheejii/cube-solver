package solver;

import algorithms.OLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cube.Algorithm;
import cube.CubeOrientation;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.OrientationFrames;
import cube.OrientedCube;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
        return solveStage(cube.copy(), new CubeOrientation());
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveStage(cube.copy(), OrientationFrames.orientedFrameFor(crossFace)));
    }

    public Algorithm solve(OrientedCube cube) {
        return solveStage(cube.cubeState().copy(), cube.orientation());
    }

    public List<Algorithm> solveCandidates(OrientedCube cube) {
        return solveStageCandidates(cube.cubeState().copy(), cube.orientation());
    }

    private Algorithm solveStage(CubeState cube, CubeOrientation orientation) {
        return solveStageCandidates(cube, orientation).get(0);
    }

    private List<Algorithm> solveStageCandidates(CubeState cube, CubeOrientation orientation) {
        if (caseDatabase == null || caseDatabase.size() == 0) {
            throw new IllegalStateException("OLL solver requires a non-empty OLL case database");
        }

        ensurePreconditions(cube, orientation);
        if (OLLAnalyzer.isOllSolved(cube, orientation)) {
            return List.of(new Algorithm());
        }

        var candidates = new LinkedHashMap<String, Algorithm>();
        for (var prefix : AUF_TRIALS) {
            SolveCancellation.throwIfCancelled();
            var trialCube = cube.copy();
            var resultingOrientation = executeAndReturnOrientation(trialCube, orientation, prefix.getMoves());
            var signature = OLLAnalyzer.extractSignature(trialCube, resultingOrientation);
            var orientationKey = CubeOrientationKey.from(resultingOrientation);
            int candidatesBeforeLookup = candidates.size();
            for (var ollCase : caseDatabase.findAll(orientationKey, signature)) {
                for (var solved : findSolvedCandidates(cube, orientation, prefix, ollCase.algorithm())) {
                    candidates.putIfAbsent(solved.toString(), solved);
                }
            }

            if (candidates.size() == candidatesBeforeLookup) {
                for (var ollCase : caseDatabase.allCases()) {
                    SolveCancellation.throwIfCancelled();
                    for (var solved : findSolvedCandidates(cube, orientation, prefix, ollCase.algorithm())) {
                        candidates.putIfAbsent(solved.toString(), solved);
                    }
                }
            }
        }

        var sorted = candidates.values().stream()
                .sorted(Comparator
                        .comparingInt(Algorithm::getMoveCount)
                        .thenComparingInt(OLLSolver::cubeRotationCount)
                        .thenComparingInt(algorithm -> algorithm.getMoves().size())
                        .thenComparing(Algorithm::toString))
                .toList();
        if (sorted.isEmpty()) {
            throw new IllegalStateException("No OLL case match found for current last-layer orientation");
        }
        return distinctResultStates(cube, orientation, sorted);
    }

    private static boolean solvesOll(CubeState cube, CubeOrientation orientation, Algorithm candidate) {
        var validationCube = cube.copy();
        var finalOrientation = executeAndReturnOrientation(validationCube, orientation, candidate.getMoves());
        return CrossAnalyzer.isCrossSolved(validationCube, finalOrientation)
                && F2LAnalyzer.isF2LSolved(validationCube, finalOrientation)
                && OLLAnalyzer.isOllSolved(validationCube, finalOrientation);
    }

    private static List<Algorithm> findSolvedCandidates(
            CubeState cube,
            CubeOrientation orientation,
            Algorithm prefix,
            Algorithm algorithm
    ) {
        var candidate = Algorithm.normalize(prefix.concat(algorithm));
        if (solvesOll(cube, orientation, candidate)) {
            return List.of(candidate);
        }
        return List.of();
    }

    private static List<Algorithm> distinctResultStates(
            CubeState cube,
            CubeOrientation orientation,
            List<Algorithm> candidates
    ) {
        var byState = new LinkedHashMap<String, Algorithm>();
        for (var candidate : candidates) {
            SolveCancellation.throwIfCancelled();
            var resultCube = cube.copy();
            var resultOrientation = executeAndReturnOrientation(
                    resultCube,
                    orientation,
                    candidate.getMoves()
            );
            var key = Arrays.toString(resultCube.cornerPerm)
                    + Arrays.toString(resultCube.cornerOri)
                    + Arrays.toString(resultCube.edgePerm)
                    + Arrays.toString(resultCube.edgeOri)
                    + "|U=" + resultOrientation.faceAt(Face.U)
                    + "|R=" + resultOrientation.faceAt(Face.R)
                    + "|F=" + resultOrientation.faceAt(Face.F);
            byState.putIfAbsent(key, candidate);
        }
        return List.copyOf(byState.values());
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

    private static int cubeRotationCount(Algorithm algorithm) {
        int count = 0;
        for (var move : algorithm.getMoves()) {
            if (move.isCubeRotation()) {
                count++;
            }
        }
        return count;
    }

}
