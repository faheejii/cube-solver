package solver;

import algorithms.F2LCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cube.*;

import java.util.ArrayList;

public class SolverMain {
    public static void main(String[] args) {
        var crossFace = args.length > 0 ? Face.fromNotation(args[0].charAt(0)) : Face.D;

        var scrambles = new ArrayList<String>();
        scrambles.add("U F' U L' B2 U2 B L' U2 F U2 F' L2 F B U2");
        scrambles.add("R U2 F U2 R2 F R2 B F2 U2 B2 R B L' R2 U2 L U");
        scrambles.add("U L' U F' U F' L2 F2 R U2 R' F2 L F2 U2 L");
        scrambles.add("R' U2 B2 L2 F2 L D2 L' D F2 L2 B2 U' R U'");
        scrambles.add("U2 R2 F2 D B2 D' F2 D B2 D' R U' F R' F' R U R");
        scrambles.add("D2 B2 U' B2 R2 D' R2 D' F2 D F2 B' R B D' F2 U' F2");

        var count = 1;
        for (var scramble : scrambles) {
            long startTime = System.nanoTime();
            var cube = new CubeState();
            MoveApplier.applyAlgorithm(cube, scramble);
            var crossSolver = new CrossSolver();
            var crossSolution = crossSolver.solve(cube, crossFace);
            MoveApplier.executeMoves(cube, crossSolution.getMoves());
            var f2lSolver = new F2LSolver(F2LCaseDatabase.seedBasicCases());
            var f2lSolution = f2lSolver.solveAfterCross(cube, crossFace);
            MoveApplier.executeMoves(cube, f2lSolution.getMoves());

            System.out.printf("%d.", count);
            System.out.println("Selected face: " + crossFace);
            System.out.println("Scramble: " + scramble);
            System.out.println("Cross solution: " + crossSolution);
            System.out.println("Cross solved for selected face: " + CrossAnalyzer.isCrossSolved(cube, crossFace));
            System.out.println("F2L solution: " + f2lSolution);
            System.out.println("Solved F2L slots for selected face: " + solvedSlotSummary(cube, crossFace));
            System.out.println("Cross solution length: " + crossSolution.getMoveCount());
            System.out.println("F2L solution length: " + f2lSolution.getMoveCount());
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
}
