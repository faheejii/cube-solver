package solver;

import algorithms.F2LCase;
import algorithms.F2LCaseDatabase;
import algorithms.OLLCase;
import algorithms.OLLCaseDatabase;
import cfop.F2LCaseSignature;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.OLLCaseSignature;
import cube.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SolverMain {
    public static void main(String[] args) {
        var crossFace = args.length > 0 ? Face.fromNotation(args[0].charAt(0)) : Face.D;
        printCollisionReport("F2L", F2LCaseDatabase.duplicateSeedBasicCases());
        printCollisionReport("OLL", OLLCaseDatabase.duplicateSeedCases());

        var scrambles = new ArrayList<String>();
        scrambles.add("R U2 R' U' R U' R' U'");

        var count = 1;
        for (var scramble : scrambles) {
            long startTime = System.nanoTime();
            var cube = new CubeState();
            MoveApplier.applyAlgorithm(cube, scramble);
            var orientedCube = new OrientedCube(cube);
            var crossSolver = new CrossSolver();
            var crossSolution = crossSolver.solve(orientedCube.cubeState(), crossFace);
            orientedCube.applyMoves(crossSolution.getMoves());
            var database = F2LCaseDatabase.seedBasicCases();
            var f2lSolver = new F2LSolver(database);
            var f2lSolution = f2lSolver.solve(orientedCube);
            orientedCube.applyMoves(f2lSolution.getMoves());
            var ollDatabase = OLLCaseDatabase.seedCases();
            Algorithm ollSolution = new Algorithm();
            String ollStatus = "skipped (no seeded OLL cases)";
            if (ollDatabase.size() > 0) {
                try {
                    var ollSolver = new OLLSolver(ollDatabase);
                    ollSolution = ollSolver.solve(orientedCube);
                    orientedCube.applyMoves(ollSolution.getMoves());
                    ollStatus = Boolean.toString(OLLAnalyzer.isOllSolved(cube, orientedCube.orientation()));
                } catch (IllegalStateException e) {
                    ollStatus = "not solved (" + e.getMessage() + ")";
                }
            }

            System.out.printf("%d.", count);
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
            System.out.printf("Elapsed time: %.3f ms%n%n", (System.nanoTime() - startTime) / 1_000_000.0);
            count++;
        }
    }

    private static String solvedSlotSummary(CubeState cube, Face crossFace) {
        var solved = new ArrayList<String>();
        for (var slot : F2LAnalyzer.getSolvedSlots(cube, crossFace)) {
            solved.add(slot.name());
        }
        return solved.toString();
    }

    private static <S, C> void printCollisionReport(String label, Map<S, List<C>> collisions) {
        System.out.println(label + " signature collisions:");
        if (collisions.isEmpty()) {
            System.out.println("  none");
            return;
        }

        for (var entry : collisions.entrySet()) {
            var cases = entry.getValue();
            System.out.println("  signature: " + entry.getKey());
            System.out.println("    kept by current DB map: " + caseSummary(cases.get(0)));
            for (int i = 1; i < cases.size(); i++) {
                System.out.println("    collides with: " + caseSummary(cases.get(i)));
            }
        }
    }

    private static String caseSummary(Object cubeCase) {
        if (cubeCase instanceof F2LCase f2lCase) {
            return f2lCase.name() + " -> " + f2lCase.algorithm();
        }
        if (cubeCase instanceof OLLCase ollCase) {
            return ollCase.name() + " -> " + ollCase.algorithm();
        }
        return String.valueOf(cubeCase);
    }
}
