package solver;

import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.MoveApplier;

import java.util.ArrayList;

public class CrossSolverMain {
    public static void main(String[] args) {
        var crossFace = args.length > 0 ? Face.fromNotation(args[0].charAt(0)) : Face.U;

        var scrambles = new ArrayList<String>();
        scrambles.add("R U F' L2 D B'");
        scrambles.add("D R B' U2 L2 U2 B' R2 B2 R2 F D2 B R2 L' D B U F D U");
        scrambles.add("U F2 D L2 R2 D2 F2 U' L2 U' R2 F' D L R' D' B2 F R F L'");
        scrambles.add("B2 D2 B2 D' L2 U F2 L2 B2 U B' D2 L B2 L2 D' B' R' F' U'");
        scrambles.add("F2 R' F R2 B2 L2 B' R2 B' D2 F D2 U2 R2 D' F R F' R F");
        scrambles.add("L' U2 L2 U2 F2 R' B2 U2 L B2 R F D B2 L2 U2 R' U' B2 R'");

        var count = 1;
        for (var scramble : scrambles) {
            var cube = new CubeState();
            MoveApplier.applyAlgorithm(cube, scramble);
            var solver = new CrossSolver();
            var crossSolution = solver.solve(cube, crossFace);

            MoveApplier.applyMoves(cube, crossSolution.getMoves());

            System.out.printf("%d.", count);
            System.out.println("Selected face: " + crossFace);
            System.out.println("Scramble: " + scramble);
            System.out.println("Cross solution: " + crossSolution);
            System.out.println("Target edges on D: " + targetSummary(cube));
            System.out.println("Cross solution length: " + crossSolution.getMoveCount() + "\n");
            count++;
        }
    }

    private static String targetSummary(CubeState cube) {
        return Edge.values()[cube.edgePerm[Edge.DF.ordinal()]] + " "
                + Edge.values()[cube.edgePerm[Edge.DR.ordinal()]] + " "
                + Edge.values()[cube.edgePerm[Edge.DB.ordinal()]] + " "
                + Edge.values()[cube.edgePerm[Edge.DL.ordinal()]];
    }
}
