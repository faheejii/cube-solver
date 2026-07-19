package cfop;

import cube.CubeState;
import cube.Face;
import cube.CubeOrientation;
import cube.LogicalFaceletView;
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
        var view = LogicalFaceletView.of(cube, orientation);
        int solved = 0;

        if (isSolvedCrossEdge(view, Face.F, 1, 7)) {
            solved++;
        }
        if (isSolvedCrossEdge(view, Face.R, 5, 7)) {
            solved++;
        }
        if (isSolvedCrossEdge(view, Face.B, 7, 7)) {
            solved++;
        }
        if (isSolvedCrossEdge(view, Face.L, 3, 7)) {
            solved++;
        }

        return solved;
    }

    private static boolean isSolvedCrossEdge(
            LogicalFaceletView view,
            Face side,
            int downIndex,
            int sideIndex
    ) {
        return view.sticker(Face.D, downIndex) == Face.D
                && view.sticker(side, sideIndex) == side;
    }
}
