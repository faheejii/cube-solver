package solver;

import cube.CubeState;
import cube.Algorithm;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class F2LPruningHeuristicTest {
    @Test
    void normalizedCombinedBound_shouldBeZeroForSolvedCubeAndNeverExceedKnownF2lRoute() {
        var solved = LogicalFrameNormalizer.normalize(new CubeState(), new cube.CubeOrientation());
        assertEquals(0, maxCombinedDistance(solved));

        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, "R U F2 L' D B U2 R' F D2 L B2 U'");
        var postCross = new OrientedCube(scrambled);
        var cross = new CrossSolver().solve(scrambled, Face.D);
        postCross.applyMoves(cross.getMoves());

        var normalized = LogicalFrameNormalizer.normalize(postCross.cubeState(), postCross.orientation());
        var knownRoute = new F2LSolver().solveTrace(new OrientedCube(
                postCross.cubeState().copy(), postCross.orientation()
        ));

        assertTrue(maxCombinedDistance(normalized) <= knownRoute.algorithm().getMoveCount());
    }

    @Test
    void logicalFrameNormalization_shouldAgreeAcrossEquivalentOrientedStates() {
        var canonical = new OrientedCube();
        canonical.applyAlgorithm("R U F2 L' D B U2");
        var canonicalState = LogicalFrameNormalizer.normalize(canonical.cubeState(), canonical.orientation());

        var rotatedFrame = new cube.CubeOrientation();
        rotatedFrame.applyRotation(cube.Move.Z2);
        rotatedFrame.applyRotation(cube.Move.Y);
        rotatedFrame.applyRotation(cube.Move.X_PRIME);
        var reoriented = new OrientedCube(new CubeState(), rotatedFrame);
        reoriented.applyAlgorithm("R U F2 L' D B U2");
        var normalized = LogicalFrameNormalizer.normalize(reoriented.cubeState(), reoriented.orientation());

        assertEquals(F2LStateCodec.cubeKey(canonicalState), F2LStateCodec.cubeKey(normalized));
        assertEquals(maxCombinedDistance(canonicalState), maxCombinedDistance(normalized));
    }

    @Test
    void combinedHeuristicMetric_shouldAcceptOuterTurnsAndRotationsOnly() {
        assertTrue(F2LSolver.supportsOuterTurnMetric(Algorithm.parse("x U R2 y'")));
        assertTrue(F2LSolver.supportsOuterTurnMetric(new Algorithm()));
        assertFalse(F2LSolver.supportsOuterTurnMetric(Algorithm.parse("r U")));
        assertFalse(F2LSolver.supportsOuterTurnMetric(Algorithm.parse("M F")));
    }

    private static int maxCombinedDistance(CubeState state) {
        return java.util.Arrays.stream(PruningTables.combinedDistances(state)).max().orElse(0);
    }
}
