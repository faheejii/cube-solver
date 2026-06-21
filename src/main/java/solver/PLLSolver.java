package solver;

import algorithms.PLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.Algorithm;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.OrientationFrames;
import cube.OrientedCube;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class PLLSolver {
    private static final Algorithm[] AUF_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME))
    };

    private final PLLCaseDatabase caseDatabase;

    public PLLSolver() {
        this(null);
    }

    public PLLSolver(PLLCaseDatabase caseDatabase) {
        this.caseDatabase = caseDatabase;
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
            throw new IllegalStateException("PLL solver requires a non-empty PLL case database");
        }

        ensurePreconditions(cube, orientation);
        if (PLLAnalyzer.isPllSolved(cube, orientation)) {
            return new Algorithm();
        }

        var candidates = new LinkedHashMap<String, Algorithm>();
        for (var preAuf : AUF_TRIALS) {
            SolveCancellation.throwIfCancelled();
            var trialCube = cube.copy();
            var trialOrientation = executeAndReturnOrientation(trialCube, orientation, preAuf.getMoves());
            if (isFullySolved(trialCube, trialOrientation)) {
                candidates.putIfAbsent(preAuf.toString(), preAuf);
            }

            var signature = PLLAnalyzer.extractSignature(trialCube, trialOrientation);
            var match = caseDatabase.find(signature);
            int candidatesBeforeLookup = candidates.size();
            if (match.isPresent()) {
                for (var solved : findSolvedCandidates(cube, orientation, preAuf, match.get().algorithm())) {
                    candidates.putIfAbsent(solved.toString(), solved);
                }
            }

            if (candidates.size() == candidatesBeforeLookup) {
                for (var pllCase : caseDatabase.allCases()) {
                    if (match.isPresent() && pllCase == match.get()) {
                        continue;
                    }
                    for (var solved : findSolvedCandidates(cube, orientation, preAuf, pllCase.algorithm())) {
                        candidates.putIfAbsent(solved.toString(), solved);
                    }
                }
            }
        }

        return candidates.values().stream()
                .min(Comparator
                        .comparingInt(Algorithm::getMoveCount)
                        .thenComparingInt(algorithm -> algorithm.getMoves().size())
                        .thenComparing(Algorithm::toString))
                .orElseThrow(() -> new IllegalStateException(
                        "No PLL case match found for current last-layer permutation"
                ));
    }

    private static void ensurePreconditions(CubeState cube, CubeOrientation orientation) {
        if (!CrossAnalyzer.isCrossSolved(cube, orientation)) {
            throw new IllegalArgumentException("PLL requires a solved cross");
        }
        if (!F2LAnalyzer.isF2LSolved(cube, orientation)) {
            throw new IllegalArgumentException("PLL requires solved F2L");
        }
        if (!OLLAnalyzer.isOllSolved(cube, orientation)) {
            throw new IllegalArgumentException("PLL requires solved OLL");
        }
    }

    private static boolean isFullySolved(CubeState cube, CubeOrientation orientation) {
        return CrossAnalyzer.isCrossSolved(cube, orientation)
                && F2LAnalyzer.isF2LSolved(cube, orientation)
                && OLLAnalyzer.isOllSolved(cube, orientation)
                && PLLAnalyzer.isPllSolved(cube, orientation);
    }

    private static List<Algorithm> findSolvedCandidates(
            CubeState cube,
            CubeOrientation orientation,
            Algorithm preAuf,
            Algorithm algorithm
    ) {
        var solved = new java.util.ArrayList<Algorithm>();
        for (var postAuf : AUF_TRIALS) {
            var legacy = preAuf.concat(algorithm).concat(postAuf);
            var displayed = Algorithm.parse(legacy.toString());
            if (solvesPll(cube, orientation, displayed)) {
                solved.add(displayed);
            }
            if (!legacy.getMoves().equals(displayed.getMoves()) && solvesPll(cube, orientation, legacy)) {
                solved.add(legacy);
            }
        }
        return List.copyOf(solved);
    }

    private static boolean solvesPll(CubeState cube, CubeOrientation orientation, Algorithm candidate) {
        var validationCube = cube.copy();
        var finalOrientation = executeAndReturnOrientation(validationCube, orientation, candidate.getMoves());
        return isFullySolved(validationCube, finalOrientation);
    }

    private static CubeOrientation executeAndReturnOrientation(CubeState cube, CubeOrientation orientation, List<Move> moves) {
        var orientedCube = new OrientedCube(cube, orientation);
        orientedCube.applyMoves(moves);
        return orientedCube.orientation();
    }

}
