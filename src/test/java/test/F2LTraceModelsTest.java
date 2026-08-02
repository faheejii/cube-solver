package test;

import cfop.F2LCaseSignature;
import cfop.F2LGeometry.TargetSlot;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Corner;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.CubeStateSnapshot;
import cube.Edge;
import cube.Move;
import org.junit.jupiter.api.Test;
import solver.F2LCaseDescription;
import solver.F2LPairStep;
import solver.F2LReasonCode;
import solver.F2LSelectionEvidence;
import solver.F2LSolveTrace;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LTraceModelsTest {
    @Test
    void trace_shouldBeImmutableAndReconstructItsPairAlgorithms() {
        var setup = new ArrayList<>(List.of(Move.U));
        var complete = new ArrayList<>(List.of(Move.U, Move.R, Move.R_PRIME));
        var step = pairStep(setup, List.of(Move.R), List.of(Move.R_PRIME), complete);
        var trace = new F2LSolveTrace(complete, List.of(step), false);

        setup.clear();
        complete.clear();

        assertEquals("U R R'", trace.algorithm().toString());
        assertTrue(trace.algorithmMatchesPairSteps());
        assertFalse(trace.hasCompletePairTrace());
        assertThrows(UnsupportedOperationException.class, () -> trace.algorithmMoves().add(Move.U));
        assertThrows(UnsupportedOperationException.class, () -> trace.pairSteps().add(step));
    }

    @Test
    void pairStep_shouldRejectBreakdownThatDoesNotMatchCompleteMoves() {
        assertThrows(IllegalArgumentException.class, () -> pairStep(
                List.of(Move.U), List.of(Move.R), List.of(Move.R_PRIME), List.of(Move.U, Move.F)
        ));
    }

    @Test
    void cubeStateSnapshot_shouldDefensivelyCopyState() {
        var cube = new CubeState();
        var snapshot = CubeStateSnapshot.from(cube);
        cube.cornerPerm[0] = 7;

        assertEquals(0, snapshot.toCubeState().cornerPerm[0]);
        assertEquals(snapshot, CubeStateSnapshot.from(snapshot.toCubeState()));
    }

    private static F2LPairStep pairStep(
            List<Move> setup,
            List<Move> pairing,
            List<Move> insertion,
            List<Move> complete
    ) {
        return new F2LPairStep(
                1,
                Corner.DFR,
                Edge.FR,
                F2LSlot.FR,
                setup,
                pairing,
                insertion,
                complete,
                true,
                CubeStateSnapshot.from(new CubeState()),
                CubeOrientationKey.from(new cube.CubeOrientation()),
                CubeStateSnapshot.from(new CubeState()),
                CubeOrientationKey.from(new cube.CubeOrientation()),
                F2LPreservationMask.empty(),
                new F2LCaseDescription(
                        new F2LCaseSignature(Corner.URF, 0, Edge.UF, 0),
                        false,
                        false,
                        false
                ),
                new F2LSelectionEvidence(
                        complete.size(), 0, complete.size(), 0,
                        true, false, true, false,
                        List.of(F2LReasonCode.SHORTEST_AVAILABLE_PAIR)
                )
        );
    }
}
