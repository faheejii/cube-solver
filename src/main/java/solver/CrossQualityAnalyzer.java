package solver;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cube.CubeState;
import cube.MoveApplier;
import cube.OrientedCube;

/** Derives the F2L state created by the visible cross algorithm. */
public final class CrossQualityAnalyzer {
    private CrossQualityAnalyzer() {
    }

    /** Returns null when the cross is not solved in the resulting frame. */
    public static Integer solvedF2lPairCount(OrientedCube postCrossCube) {
        if (!CrossAnalyzer.isCrossSolved(postCrossCube.cubeState(), postCrossCube.orientation())) {
            return null;
        }
        return F2LAnalyzer.countSolvedSlots(postCrossCube.cubeState(), postCrossCube.orientation());
    }

    /** Replays stored inputs so older saved solutions need no persisted metadata. */
    public static Integer solvedF2lPairCount(String scramble, String crossAlgorithm) {
        if (scramble == null || crossAlgorithm == null) {
            return null;
        }
        try {
            var cube = new CubeState();
            MoveApplier.applyAlgorithm(cube, scramble);
            var replay = new OrientedCube(cube);
            replay.applyAlgorithm(crossAlgorithm);
            return solvedF2lPairCount(replay);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
