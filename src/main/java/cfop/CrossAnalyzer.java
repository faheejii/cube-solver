package cfop;

import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.CubeOrientation;
import cube.OrientationFrames;

public class CrossAnalyzer {
    public static boolean isCrossSolved(CubeState cube) {
        return countSolvedCrossEdges(cube) == 4;
    }

    public static boolean isCrossSolved(CubeState cube, Face crossFace) {
        return countSolvedCrossEdges(cube, OrientationFrames.orientedFrameFor(crossFace)) == 4;
    }

    public static boolean isCrossSolved(CubeState cube, CubeOrientation orientation) {
        return countSolvedCrossEdges(cube, orientation) == 4;
    }

    public static int countSolvedCrossEdges(CubeState cube) {
        return countSolvedCrossEdges(cube, new CubeOrientation());
    }

    public static int countSolvedCrossEdges(CubeState cube, Face crossFace) {
        return countSolvedCrossEdges(cube, OrientationFrames.orientedFrameFor(crossFace));
    }

    public static int countSolvedCrossEdges(CubeState cube, CubeOrientation orientation) {
        int solved = 0;

        if (isSolvedCrossEdge(cube, edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F)))) {
            solved++;
        }
        if (isSolvedCrossEdge(cube, edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.R)))) {
            solved++;
        }
        if (isSolvedCrossEdge(cube, edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B)))) {
            solved++;
        }
        if (isSolvedCrossEdge(cube, edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.L)))) {
            solved++;
        }

        return solved;
    }

    private static boolean isSolvedCrossEdge(CubeState cube, Edge edge) {
        return cube.edgePerm[edge.ordinal()] == edge.ordinal() && cube.edgeOri[edge.ordinal()] == 0;
    }

    private static Edge edgeForFaces(Face first, Face second) {
        if (matches(first, second, Face.U, Face.R)) return Edge.UR;
        if (matches(first, second, Face.U, Face.F)) return Edge.UF;
        if (matches(first, second, Face.U, Face.L)) return Edge.UL;
        if (matches(first, second, Face.U, Face.B)) return Edge.UB;
        if (matches(first, second, Face.D, Face.R)) return Edge.DR;
        if (matches(first, second, Face.D, Face.F)) return Edge.DF;
        if (matches(first, second, Face.D, Face.L)) return Edge.DL;
        if (matches(first, second, Face.D, Face.B)) return Edge.DB;
        if (matches(first, second, Face.F, Face.R)) return Edge.FR;
        if (matches(first, second, Face.F, Face.L)) return Edge.FL;
        if (matches(first, second, Face.B, Face.R)) return Edge.BR;
        if (matches(first, second, Face.B, Face.L)) return Edge.BL;
        throw new IllegalArgumentException("Faces do not form an edge: " + first + ", " + second);
    }

    private static boolean matches(Face first, Face second, Face expectedA, Face expectedB) {
        return (first == expectedA && second == expectedB) || (first == expectedB && second == expectedA);
    }
}
