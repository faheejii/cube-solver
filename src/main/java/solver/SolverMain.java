package solver;

import algorithms.F2LCaseDatabase;
import algorithms.OLLCaseDatabase;
import algorithms.PLLCaseDatabase;
import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;

import java.util.ArrayList;
import java.util.List;

public class SolverMain {
    private static final List<String> DEFAULT_SCRAMBLES = List.of(
            "F' L' U2 B L' D B U' F2 R2 F2 R U2 D2 L' D2 F2 L2 D2 F",
            //"F' U2 F U2 B U2 R2 F D2 U2 L U' L2 B U L R B2 U F",
            //"D' R B L2 F2 D' U2 R2 U' B2 F2 D2 U' R2 F L' B2 D B D2 U'",
            //"R F U D B L2 F' L U' R' D2 B2 R2 U L2 D' B2 R2 D B2 U2",
            "U2 R2 B2 D' L2 U F2 D2 R2 B2 R2 D F' R' B2 L' B' R2 B2 L' D2"
    );

    public static void main(String[] args) {
        var crossFace = args.length > 0 ? Face.fromNotation(args[0].charAt(0)) : Face.U;
        var scrambles = args.length > 1 ? parseScrambles(args, 1) : DEFAULT_SCRAMBLES;

        var f2lCollisions = F2LCaseDatabase.duplicateSeedBasicCases();
        var ollCollisions = OLLCaseDatabase.duplicateSeedCases();
        var pllCollisions = PLLCaseDatabase.duplicateSeedCases();

        System.out.println("F2L collisions: " + f2lCollisions);
        System.out.println("OLL collisions: " + ollCollisions);
        System.out.println("PLL collisions: " + pllCollisions);
        System.out.println("Selected face: " + crossFace);
        System.out.println("Scramble count: " + scrambles.size());

        for (int i = 0; i < scrambles.size(); i++) {
            solveScramble(scrambles.get(i), crossFace, i + 1, scrambles.size());
        }
    }

    private static void solveScramble(String scramble, Face crossFace, int index, int total) {
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

        Algorithm pllSolution = new Algorithm();
        var pllStatus = "skipped (no seeded PLL cases)";
        var pllDatabase = PLLCaseDatabase.seedCases();
        if (pllDatabase.size() > 0) {
            try {
                pllSolution = new PLLSolver(pllDatabase).solve(orientedCube);
                orientedCube.applyMoves(pllSolution.getMoves());
                pllStatus = Boolean.toString(PLLAnalyzer.isPllSolved(cube, orientedCube.orientation()));
            } catch (IllegalStateException e) {
                pllStatus = "not solved (" + e.getMessage() + ")";
            }
        }

        System.out.println();
        System.out.println("=== Scramble " + index + "/" + total + " ===");
        System.out.println("Scramble: " + scramble);
        System.out.println("Cross solution: " + crossSolution);
        System.out.println("Cross solved for selected face: " + CrossAnalyzer.isCrossSolved(cube, crossFace));
        System.out.println("F2L solution: " + f2lSolution);
        System.out.println("Solved F2L slots for selected face: " + solvedSlotSummary(cube, crossFace));
        System.out.println("OLL solution: " + ollSolution);
        System.out.println("OLL solved in current frame: " + ollStatus);
        System.out.println("PLL solution: " + pllSolution);
        System.out.println("PLL solved in current frame: " + pllStatus);
        System.out.println("Full cube solved in current frame: " + isFullySolved(cube, orientedCube));
        System.out.println("Cross solution length: " + crossSolution.getMoveCount());
        System.out.println("F2L solution length: " + f2lSolution.getMoveCount());
        System.out.println("OLL solution length: " + ollSolution.getMoveCount());
        System.out.println("PLL solution length: " + pllSolution.getMoveCount());
        System.out.println("Total solution length: " + (crossSolution.getMoveCount() + f2lSolution.getMoveCount() + ollSolution.getMoveCount() + pllSolution.getMoveCount()));
        System.out.printf("Elapsed time: %.3f ms%n", (System.nanoTime() - startTime) / 1_000_000.0);
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

    private static List<String> parseScrambles(String[] args, int startIndex) {
        var rawScrambles = joinArgs(args, startIndex).split("\\s*;\\s*");
        var scrambles = new ArrayList<String>();
        for (var scramble : rawScrambles) {
            if (!scramble.isBlank()) {
                scrambles.add(scramble.trim());
            }
        }
        if (scrambles.isEmpty()) {
            throw new IllegalArgumentException("At least one scramble is required");
        }
        return List.copyOf(scrambles);
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
