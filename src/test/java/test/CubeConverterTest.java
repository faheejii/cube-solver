package test;

import cube.CubeState;
import cube.MoveApplier;
import io.CubeConverter;
import io.FaceletState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CubeConverterTest {
    @Test
    void solvedCube_shouldConvertToSolvedFacelets() {
        FaceletState facelets = CubeConverter.toFaceletState(new CubeState());

        assertEquals("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB", facelets.toNotation());
    }

    @Test
    void movedCube_shouldRoundTripThroughFaceletState() {
        CubeState cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U F' L2 D B'");

        FaceletState facelets = CubeConverter.toFaceletState(cube);
        CubeState roundTripped = CubeConverter.toCubeState(facelets);

        assertArrayEquals(cube.cornerPerm, roundTripped.cornerPerm);
        assertArrayEquals(cube.cornerOri, roundTripped.cornerOri);
        assertArrayEquals(cube.edgePerm, roundTripped.edgePerm);
        assertArrayEquals(cube.edgeOri, roundTripped.edgeOri);
    }
}
