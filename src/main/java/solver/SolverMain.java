package solver;

import cfop.F2LSlot;
import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.MoveApplier;

import java.util.ArrayList;

public class SolverMain {
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
            var crossSolver = new CrossSolver();
            var crossSolution = crossSolver.solve(cube, crossFace);

            MoveApplier.applyMoves(cube, crossSolution.getMoves());

            var f2lSolver = new F2LSolver();
            var f2lSolution = f2lSolver.solveAfterCross(cube, crossFace);
            MoveApplier.applyMoves(cube, f2lSolution.getMoves());

            System.out.printf("%d.", count);
            System.out.println("Selected face: " + crossFace);
            System.out.println("Scramble: " + scramble);
            System.out.println("Cross solution: " + crossSolution);
            System.out.println("Cross target edges on D: " + targetSummary(cube));
            System.out.println("F2L solution: " + f2lSolution);
            System.out.println("Solved F2L slots on D frame: " + solvedSlotSummary(cube, crossFace));
            System.out.println("Cross solution length: " + crossSolution.getMoveCount());
            System.out.println("F2L solution length: " + f2lSolution.getMoveCount() + "\n");
            count++;
        }
    }

    private static String targetSummary(CubeState cube) {
        return Edge.values()[cube.edgePerm[Edge.DF.ordinal()]] + " "
                + Edge.values()[cube.edgePerm[Edge.DR.ordinal()]] + " "
                + Edge.values()[cube.edgePerm[Edge.DB.ordinal()]] + " "
                + Edge.values()[cube.edgePerm[Edge.DL.ordinal()]];
    }

    private static String solvedSlotSummary(CubeState cube, Face crossFace) {
        var solved = new ArrayList<String>();
        for (var slot : targetSlotsForSelectedFace(crossFace)) {
            var cornerSolved = cube.cornerPerm[slot.cornerPosition().ordinal()] == slot.cornerPiece().ordinal()
                    && cube.cornerOri[slot.cornerPosition().ordinal()] == 0;
            var edgeSolved = cube.edgePerm[slot.edgePosition().ordinal()] == slot.edgePiece().ordinal()
                    && cube.edgeOri[slot.edgePosition().ordinal()] == 0;
            if (cornerSolved && edgeSolved) {
                solved.add(slot.name());
            }
        }
        return solved.toString();
    }

    private static TargetSlot[] targetSlotsForSelectedFace(Face face) {
        var solved = new CubeState();
        var orientation = switch (face) {
            case D -> "";
            case U -> "z2";
            case R -> "z";
            case L -> "z'";
            case F -> "x";
            case B -> "x'";
        };
        MoveApplier.applyAlgorithm(solved, orientation);
        return new TargetSlot[]{
                new TargetSlot(
                        "FR",
                        Corner.DFR,
                        Corner.values()[solved.cornerPerm[Corner.DFR.ordinal()]],
                        Edge.FR,
                        Edge.values()[solved.edgePerm[Edge.FR.ordinal()]]
                ),
                new TargetSlot(
                        "FL",
                        Corner.DLF,
                        Corner.values()[solved.cornerPerm[Corner.DLF.ordinal()]],
                        Edge.FL,
                        Edge.values()[solved.edgePerm[Edge.FL.ordinal()]]
                ),
                new TargetSlot(
                        "BL",
                        Corner.DBL,
                        Corner.values()[solved.cornerPerm[Corner.DBL.ordinal()]],
                        Edge.BL,
                        Edge.values()[solved.edgePerm[Edge.BL.ordinal()]]
                ),
                new TargetSlot(
                        "BR",
                        Corner.DRB,
                        Corner.values()[solved.cornerPerm[Corner.DRB.ordinal()]],
                        Edge.BR,
                        Edge.values()[solved.edgePerm[Edge.BR.ordinal()]]
                )
        };
    }

    private record TargetSlot(String name, Corner cornerPosition, Corner cornerPiece, Edge edgePosition, Edge edgePiece) {
    }
}
