package cfop;

import cube.CubeState;
import cube.Edge;

public class CrossAnalyzer {
    public static boolean isCrossSolved(CubeState cube) {
        return countSolvedCrossEdges(cube) == 4;
    }

    public static int countSolvedCrossEdges(CubeState cube) {
        int solved = 0;

        if (isSolvedCrossEdge(cube, Edge.DF)) {
            solved++;
        }
        if (isSolvedCrossEdge(cube, Edge.DR)) {
            solved++;
        }
        if (isSolvedCrossEdge(cube, Edge.DB)) {
            solved++;
        }
        if (isSolvedCrossEdge(cube, Edge.DL)) {
            solved++;
        }

        return solved;
    }

    private static boolean isSolvedCrossEdge(CubeState cube, Edge edge) {
        return cube.edgePerm[edge.ordinal()] == edge.ordinal() && cube.edgeOri[edge.ordinal()] == 0;
    }
}
