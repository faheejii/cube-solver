package solver;

import algorithms.F2LCaseDatabase;
import algorithms.OLLCaseDatabase;
import algorithms.PLLCaseDatabase;
import cube.Face;

import java.util.ArrayList;
import java.util.List;

public class SolverMain {
    private static final boolean USE_LEGACY_F2L = Boolean.getBoolean("f2l.legacy");
    private static final List<String> DEFAULT_SCRAMBLES = List.of(
            "L2 B2 D L2 B2 D' R2 U' L2 B2 D2 F' U' F D' L B U' R' U2"
    );

    public static void main(String[] args) {
        var crossFace = args.length > 0 ? Face.fromNotation(args[0].charAt(0)) : Face.U;
        var scrambles = args.length > 1 ? parseScrambles(args, 1) : DEFAULT_SCRAMBLES;

        var f2lCollisions = F2LCaseDatabase.duplicateSeedBasicCases();
        var ollCollisions = OLLCaseDatabase.duplicateSeedCases();
        var pllCollisions = PLLCaseDatabase.duplicateSeedCases();
        var solveService = new CfopSolveService();

        System.out.println("F2L collisions: " + f2lCollisions);
        System.out.println("OLL collisions: " + ollCollisions);
        System.out.println("PLL collisions: " + pllCollisions);
        System.out.println("F2L mode: " + (USE_LEGACY_F2L ? "legacy DB + validated fallback" : "two-phase DB + fallback"));
        System.out.println("Selected face: " + crossFace);
        System.out.println("Scramble count: " + scrambles.size());

        for (int i = 0; i < scrambles.size(); i++) {
            solveScramble(solveService, scrambles.get(i), crossFace, i + 1, scrambles.size());
        }
    }

    private static void solveScramble(
            CfopSolveService solveService,
            String scramble,
            Face crossFace,
            int index,
            int total
    ) {
        var result = solveService.solve(new CfopSolveRequest(scramble, crossFace, USE_LEGACY_F2L));

        System.out.println();
        System.out.println("=== Scramble " + index + "/" + total + " ===");
        System.out.println("Scramble: " + scramble);
        System.out.println("F2L setup phase cases: " + result.f2lSetupCaseCount());
        System.out.println("F2L insert phase cases: " + result.f2lInsertCaseCount());
        System.out.println("Cross solution: " + result.cross().algorithm());
        System.out.println("Cross solved for selected face: " + result.cross().solved());
        System.out.println("F2L solution: " + result.f2l().algorithm());
        System.out.println("Solved F2L slots for selected face: " + result.solvedF2LSlots());
        System.out.println("OLL solution: " + result.oll().algorithm());
        System.out.println("OLL solved in current frame: " + result.oll().status());
        System.out.println("PLL solution: " + result.pll().algorithm());
        System.out.println("PLL solved in current frame: " + result.pll().status());
        System.out.println("Full cube solved in current frame: " + result.fullySolved());
        System.out.println("Cross solution length: " + result.cross().moveCount());
        System.out.println("F2L solution length: " + result.f2l().moveCount());
        System.out.println("OLL solution length: " + result.oll().moveCount());
        System.out.println("PLL solution length: " + result.pll().moveCount());
        System.out.println("Total solution length: " + result.totalMoveCount());
        System.out.printf("Elapsed time: %.3f ms%n", result.elapsedMs());
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
