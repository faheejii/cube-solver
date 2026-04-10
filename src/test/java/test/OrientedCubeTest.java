package test;

import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrientedCubeTest {
    @Test
    void cubeRotation_shouldChangeOrientationWithoutChangingCanonicalState() {
        var orientedCube = new OrientedCube();

        orientedCube.applyMove(Move.Z2);

        assertEquals(Face.U, orientedCube.faceAt(Face.D));
        assertEquals(Face.D, orientedCube.faceAt(Face.U));
        assertSameState(new CubeState(), orientedCube.cubeState());
    }

    @Test
    void outerTurnsShouldFollowCurrentOrientation() {
        var orientedCube = new OrientedCube();
        var expected = new CubeState();

        orientedCube.applyMove(Move.X);
        orientedCube.applyMove(Move.U);
        MoveApplier.applyMove(expected, Move.F);

        assertSameState(expected, orientedCube.cubeState());
    }

    @Test
    void sliceTurnsShouldFollowCurrentOrientation() {
        var orientedCube = new OrientedCube();
        var expected = new CubeState();

        orientedCube.applyMove(Move.Y);
        orientedCube.applyMove(Move.M);
        MoveApplier.applyMove(expected, Move.S);

        assertSameState(expected, orientedCube.cubeState());
    }

    @Test
    void algorithmShouldTreatRotationsAsOrientationChanges() {
        var orientedCube = new OrientedCube();
        var expected = new CubeState();

        orientedCube.applyAlgorithm("z2 D");
        MoveApplier.applyMove(expected, Move.U);

        assertSameState(expected, orientedCube.cubeState());
        assertEquals(Face.U, orientedCube.faceAt(Face.D));
    }

    @Test
    void wideTurnsShouldFollowCurrentOrientation() {
        var orientedCube = new OrientedCube();
        var expected = new CubeState();

        orientedCube.applyMove(Move.Y);
        orientedCube.applyMove(Move.RW);
        MoveApplier.executeAlgorithm(expected, "b");

        assertSameState(expected, orientedCube.cubeState());
    }

    private static void assertSameState(CubeState expected, CubeState actual) {
        assertArrayEquals(expected.cornerPerm, actual.cornerPerm, "Corner permutation mismatch");
        assertArrayEquals(expected.cornerOri, actual.cornerOri, "Corner orientation mismatch");
        assertArrayEquals(expected.edgePerm, actual.edgePerm, "Edge permutation mismatch");
        assertArrayEquals(expected.edgeOri, actual.edgeOri, "Edge orientation mismatch");
    }
}
