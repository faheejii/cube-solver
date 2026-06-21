package solver;

import algorithms.OLLCaseDatabase;
import algorithms.PLLCaseDatabase;
import cube.Face;

import java.util.ArrayList;
import java.util.List;

public class SolverMain {
    private static final List<String> DEFAULT_SCRAMBLES = List.of(
            "D2 F2 D' L F R F' B' D' U2 F2 L2 B R2 F2 R2 D2 R2 B D2 R2"
    );

    public static void main(String[] args) {
        var colorNeutral = args.length > 0 && isColorNeutralArg(args[0]);
        var crossFace = args.length > 0 && !colorNeutral ? Face.fromNotation(args[0].charAt(0)) : Face.B;
        var f2lMode = args.length > 1 && isF2LModeArg(args[1]) ? F2LMode.fromApiValue(args[1]) : F2LMode.OPTIMIZED;
        var scrambleStartIndex = f2lMode == F2LMode.GREEDY && !(args.length > 1 && isF2LModeArg(args[1])) ? 1 : 2;
        var scrambles = args.length > scrambleStartIndex ? parseScrambles(args, scrambleStartIndex) : DEFAULT_SCRAMBLES;

        var ollCollisions = OLLCaseDatabase.duplicateSeedCases();
        var pllCollisions = PLLCaseDatabase.duplicateSeedCases();
        var solveService = new CfopSolveService();

        System.out.println("OLL signature collisions handled by validation: " + ollCollisions.size());
        System.out.println("PLL collisions: " + pllCollisions);
        System.out.println("F2L mode: two-phase DB + fallback");
        System.out.println("F2L search mode: " + f2lMode.apiValue());
        System.out.println("Selected face: " + (colorNeutral ? "Color Neutral" : crossFace));
        System.out.println("Scramble count: " + scrambles.size());

        for (int i = 0; i < scrambles.size(); i++) {
            var request = colorNeutral
                    ? CfopSolveRequest.colorNeutral(scrambles.get(i), f2lMode)
                    : new CfopSolveRequest(scrambles.get(i), crossFace, f2lMode);
            solveScramble(solveService, request, i + 1, scrambles.size());
        }
    }

    private static void solveScramble(
            CfopSolveService solveService,
            CfopSolveRequest request,
            int index,
            int total
    ) {
        var result = solveService.solve(request);

        System.out.println();
        System.out.println("=== Scramble " + index + "/" + total + " ===");
        System.out.println("Scramble: " + request.scramble());
        System.out.println("Chosen cross face: " + result.crossFace());
        System.out.println("F2L setup phase cases: " + result.f2lSetupCaseCount());
        System.out.println("F2L insert phase cases: " + result.f2lInsertCaseCount());
        System.out.println("F2L search mode: " + result.f2lMode());
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

    private static boolean isColorNeutralArg(String value) {
        var normalized = value.trim()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .toUpperCase();
        return normalized.equals("CN") || normalized.equals("COLORNEUTRAL");
    }

    private static boolean isF2LModeArg(String value) {
        var normalized = value.trim().replace("-", "_").toUpperCase();
        return normalized.equals("GREEDY")
                || normalized.equals("FAST")
                || normalized.equals("OPTIMIZED")
                || normalized.equals("OPTIMISED");
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
