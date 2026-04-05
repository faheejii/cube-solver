package test;

import cube.CubeState;
import cube.Move;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class MoveApplierTest {
    @Test
    void applyAlgorithm_shouldIgnoreNullOrBlankInput() {
        CubeState original = new CubeState();

        CubeState nullAlgorithmCube = new CubeState();
        MoveApplier.applyAlgorithm(nullAlgorithmCube, null);
        assertSameState(original, nullAlgorithmCube);

        CubeState blankAlgorithmCube = new CubeState();
        MoveApplier.applyAlgorithm(blankAlgorithmCube, "   ");
        assertSameState(original, blankAlgorithmCube);
    }

    @Test
    void applyMove_followedByInverse_shouldReturnSolvedState() {
        CubeState cube = new CubeState();

        MoveApplier.applyMove(cube, Move.R);
        MoveApplier.applyMove(cube, Move.R_PRIME);

        assertSolved(cube);
    }

    @Test
    void applySameQuarterTurnFourTimes_shouldReturnSolvedState() {
        CubeState cube = new CubeState();

        for (int i = 0; i < 4; i++) {
            MoveApplier.applyMove(cube, Move.U);
        }

        assertSolved(cube);
    }

    @Test
    void applySliceMove_followedByInverse_shouldReturnSolvedState() {
        CubeState cube = new CubeState();

        MoveApplier.applyMove(cube, Move.M);
        MoveApplier.applyMove(cube, Move.M_PRIME);

        assertSolved(cube);
    }

    @Test
    void applyCubeRotation_followedByInverse_shouldReturnSolvedState() {
        CubeState cube = new CubeState();

        MoveApplier.applyMove(cube, Move.X);
        MoveApplier.applyMove(cube, Move.X_PRIME);

        assertSolved(cube);
    }

    @Test
    void applyCubeRotationFourTimes_shouldReturnSolvedState() {
        CubeState cube = new CubeState();

        for (int i = 0; i < 4; i++) {
            MoveApplier.applyMove(cube, Move.Z);
        }

        assertSolved(cube);
    }

    @Test
    void applyCubeRotation_shouldMatchEquivalentSliceAlgorithm() {
        CubeState expected = new CubeState();
        CubeState actual = new CubeState();

        MoveApplier.applyAlgorithm(expected, "R M' L'");
        MoveApplier.applyMove(actual, Move.X);

        assertSameState(expected, actual);
    }

    @Test
    void sliceMoves_shouldMatchEquivalentOuterTurnAndRotationAlgorithms() {
        assertEquivalentAlgorithms("M", "R L' x'");
        assertEquivalentAlgorithms("E", "U' y D");
        assertEquivalentAlgorithms("S", "F' B z");
    }

    @Test
    void applyAlgorithm_thenInverseAlgorithm_shouldReturnSolvedState() {
        CubeState cube = new CubeState();

        MoveApplier.applyAlgorithm(cube, "R U R' U'");
        MoveApplier.applyAlgorithm(cube, "U R U' R'");

        assertSolved(cube);
    }

    private static void assertEquivalentAlgorithms(String first, String second) {
        CubeState firstCube = new CubeState();
        CubeState secondCube = new CubeState();

        MoveApplier.applyAlgorithm(firstCube, first);
        MoveApplier.applyAlgorithm(secondCube, second);

        assertSameState(firstCube, secondCube);
    }

    private static void assertSolved(CubeState cube) {
        assertSameState(new CubeState(), cube);
    }

    private static void assertSameState(CubeState expected, CubeState actual) {
        assertArrayEquals(expected.cornerPerm, actual.cornerPerm, "Corner permutation mismatch");
        assertArrayEquals(expected.cornerOri, actual.cornerOri, "Corner orientation mismatch");
        assertArrayEquals(expected.edgePerm, actual.edgePerm, "Edge permutation mismatch");
        assertArrayEquals(expected.edgeOri, actual.edgeOri, "Edge orientation mismatch");
    }
}
