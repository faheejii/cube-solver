package test;

import cfop.F2LCaseFrame;
import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LSlot;
import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Move;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class F2LCaseFrameTest {
    @Test
    void rotationToCanonical_shouldMapSlotsToExpectedYRotation() {
        assertEquals("", F2LCaseFrame.rotationToCanonical(F2LSlot.FR).toString());
        assertEquals(Move.Y_PRIME.toString(), F2LCaseFrame.rotationToCanonical(F2LSlot.FL).toString());
        assertEquals(Move.Y2.toString(), F2LCaseFrame.rotationToCanonical(F2LSlot.BL).toString());
        assertEquals(Move.Y.toString(), F2LCaseFrame.rotationToCanonical(F2LSlot.BR).toString());
    }

    @Test
    void caseSignature_shouldValidateOrientationRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new F2LCaseSignature(Corner.URF, 3, Edge.UR, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new F2LCaseSignature(Corner.URF, 0, Edge.UR, 2));
    }

    @Test
    void extract_shouldReturnInsertedSignatureForSolvedCubeInEverySlot() {
        var cube = new CubeState();

        assertEquals(new F2LCaseSignature(Corner.DFR, 0, Edge.FR, 0),
                F2LCaseSignatureExtractor.extract(cube, F2LSlot.FR));
        assertEquals(new F2LCaseSignature(Corner.DFR, 0, Edge.FR, 0),
                F2LCaseSignatureExtractor.extract(cube, F2LSlot.FL));
        assertEquals(new F2LCaseSignature(Corner.DFR, 0, Edge.FR, 0),
                F2LCaseSignatureExtractor.extract(cube, F2LSlot.BL));
        assertEquals(new F2LCaseSignature(Corner.DFR, 0, Edge.FR, 0),
                F2LCaseSignatureExtractor.extract(cube, F2LSlot.BR));
    }

    @Test
    void extract_shouldReturnSplitSignatureForSimpleUnpairedCase() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R'");

        var signature = F2LCaseSignatureExtractor.extract(cube, F2LSlot.FR);

        assertEquals(0, signature.edgeOrientation());
    }
}
