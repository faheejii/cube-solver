package test;

import cube.CubeState;
import cube.Algorithm;
import cube.Move;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void sliceQuarterTurns_shouldUseStandardDirections() {
        assertEdgePermutation("M", 0, 3, 2, 7, 4, 1, 6, 5, 8, 9, 10, 11);
        assertEdgePermutation("E", 0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 8);
        assertEdgePermutation("S", 2, 1, 6, 3, 0, 5, 4, 7, 8, 9, 10, 11);
    }

    @Test
    void cubeRotations_shouldMatchStandardFaceAndSliceEquivalences() {
        assertEquivalentAlgorithms("x", "R M' L'");
        assertEquivalentAlgorithms("y", "U E' D'");
        assertEquivalentAlgorithms("z", "F S B'");
    }

    @Test
    void wideMoves_shouldMatchEquivalentFaceAndSliceAlgorithms() {
        assertEquivalentExecutedAlgorithms("r", "R M'");
        assertEquivalentExecutedAlgorithms("r'", "R' M");
        assertEquivalentExecutedAlgorithms("r2", "R2 M2");
        assertEquivalentExecutedAlgorithms("u", "U E'");
        assertEquivalentExecutedAlgorithms("u'", "U' E");
        assertEquivalentExecutedAlgorithms("d", "D E");
        assertEquivalentExecutedAlgorithms("d'", "D' E'");
        assertEquivalentExecutedAlgorithms("f", "F S");
        assertEquivalentExecutedAlgorithms("f'", "F' S'");
        assertEquivalentExecutedAlgorithms("l", "L M");
        assertEquivalentExecutedAlgorithms("b", "B S'");
        assertEquivalentExecutedAlgorithms("b'", "B' S");
    }

    @Test
    void rawWideMoveApplication_shouldBeRejected() {
        var cube = new CubeState();
        assertThrows(IllegalArgumentException.class, () -> MoveApplier.applyMove(cube, Move.RW));
        assertThrows(IllegalArgumentException.class, () -> MoveApplier.applyAlgorithm(cube, "r"));
    }

    @Test
    void sliceMoves_shouldMatchEquivalentOuterTurnAndRotationAlgorithms() {
        assertEquivalentAlgorithms("M", "R L' x'");
        assertEquivalentAlgorithms("E", "U y' D'");
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

    private static void assertEquivalentExecutedAlgorithms(String first, String second) {
        CubeState firstCube = new CubeState();
        CubeState secondCube = new CubeState();

        MoveApplier.executeAlgorithm(firstCube, first);
        MoveApplier.executeAlgorithm(secondCube, second);

        assertSameState(firstCube, secondCube);
    }

    private static void assertEdgePermutation(String algorithm, int... expectedPermutation) {
        CubeState cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, algorithm);
        assertArrayEquals(expectedPermutation, toIntArray(cube.edgePerm), algorithm + " edge permutation mismatch");
    }

    private static int[] toIntArray(byte[] values) {
        var result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
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
