package test.io;

import cube.CubeState;
import cube.MoveApplier;
import io.CubeConverter;
import io.FaceletState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void withSticker_shouldKeepOriginalFaceletStateImmutable() {
        var original = new FaceletState();
        var changed = original.withSticker(cube.Face.U, 0, cube.Face.R);

        assertEquals(cube.Face.U, original.getSticker(cube.Face.U, 0));
        assertEquals(cube.Face.R, changed.getSticker(cube.Face.U, 0));
        assertNotEquals(original, changed);
    }

    @Test
    void faceletConversion_shouldPreserveStrictAndCenterParityTolerantValidation() {
        var parityMismatch = new CubeState();
        byte corner = parityMismatch.cornerPerm[0];
        parityMismatch.cornerPerm[0] = parityMismatch.cornerPerm[1];
        parityMismatch.cornerPerm[1] = corner;

        assertThrows(IllegalArgumentException.class, () -> CubeConverter.toFaceletState(parityMismatch));
        var tolerant = CubeConverter.toFaceletStateAllowingCenterParity(parityMismatch);
        for (var face : cube.Face.values()) {
            assertEquals(face, tolerant.getSticker(face, 4));
        }
    }
}
