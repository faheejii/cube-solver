package solver;

import algorithms.F2LCaseDatabase;
import algorithms.OLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;

import java.util.ArrayList;

public class SolverMain {
    private static final String DEFAULT_SCRAMBLE = "R U R' U' F2 L D";

    public static void main(String[] args) {
        var crossFace = args.length > 0 ? Face.fromNotation(args[0].charAt(0)) : Face.D;
        var scramble = args.length > 1 ? joinArgs(args, 1) : DEFAULT_SCRAMBLE;

        long startTime = System.nanoTime();
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, scramble);

        var orientedCube = new OrientedCube(cube);

        var crossSolution = new CrossSolver().solve(orientedCube.cubeState(), crossFace);
        orientedCube.applyMoves(crossSolution.getMoves());

        var f2lSolution = new F2LSolver(F2LCaseDatabase.seedBasicCases()).solve(orientedCube);
        orientedCube.applyMoves(f2lSolution.getMoves());

        Algorithm ollSolution = new Algorithm();
        var ollStatus = "skipped (no seeded OLL cases)";
        var ollDatabase = OLLCaseDatabase.seedCases();
        if (ollDatabase.size() > 0) {
            try {
                ollSolution = new OLLSolver(ollDatabase).solve(orientedCube);
                orientedCube.applyMoves(ollSolution.getMoves());
                ollStatus = Boolean.toString(OLLAnalyzer.isOllSolved(cube, orientedCube.orientation()));
            } catch (IllegalStateException e) {
                ollStatus = "not solved (" + e.getMessage() + ")";
            }
        }

        System.out.println("Selected face: " + crossFace);
        System.out.println("Scramble: " + scramble);
        System.out.println("Cross solution: " + crossSolution);
        System.out.println("Cross solved for selected face: " + CrossAnalyzer.isCrossSolved(cube, crossFace));
        System.out.println("F2L solution: " + f2lSolution);
        System.out.println("Solved F2L slots for selected face: " + solvedSlotSummary(cube, crossFace));
        System.out.println("OLL solution: " + ollSolution);
        System.out.println("OLL solved in current frame: " + ollStatus);
        System.out.println("Cross solution length: " + crossSolution.getMoveCount());
        System.out.println("F2L solution length: " + f2lSolution.getMoveCount());
        System.out.println("OLL solution length: " + ollSolution.getMoveCount());
        System.out.printf("Elapsed time: %.3f ms%n", (System.nanoTime() - startTime) / 1_000_000.0);
    }

    private static String solvedSlotSummary(CubeState cube, Face crossFace) {
        var solved = new ArrayList<String>();
        for (var slot : F2LAnalyzer.getSolvedSlots(cube, crossFace)) {
            solved.add(slot.name());
        }
        return solved.toString();
    }

    private static String joinArgs(String[] args, int startIndex) {
        var builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
