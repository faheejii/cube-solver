package test;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.F2LGeometry;
import cube.CubeState;
import cube.CubeOrientationKey;
import cube.CubeStateSnapshot;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.CrossSolver;
import solver.F2LSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LGreedyTraceTest {
    @Test
    void greedyTrace_shouldReplayEveryPairAndPreserveTheReportedAlgorithm() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "B' L B L2 D2 L D2");

        var cross = new CrossSolver().solve(cube, Face.D);
        MoveApplier.executeMoves(cube, cross.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(cube, Face.D));

        var solver = new F2LSolver();
        var trace = solver.solveTrace(new OrientedCube(cube));

        assertTrue(trace.solved());
        assertTrue(trace.hasCompletePairTrace());
        assertTrue(trace.algorithmMatchesPairSteps());
        assertEquals(trace.algorithm().toString(),
                trace.pairSteps().stream()
                        .map(step -> step.completeAlgorithm().toString())
                        .filter(text -> !text.isBlank())
                        .reduce((first, second) -> first + " " + second)
                        .orElse(""));

        var replay = new OrientedCube(cube);
        for (var step : trace.pairSteps()) {
            assertEquals(step.stateBefore(), cubeSnapshot(replay));
            assertEquals(step.orientationBefore(), CubeOrientationKey.from(replay.orientation()));

            replay.applyMoves(step.completeMoves());

            assertEquals(step.stateAfter(), cubeSnapshot(replay));
            assertEquals(step.orientationAfter(), CubeOrientationKey.from(replay.orientation()));
            assertTrue(F2LGeometry.isTargetSlotSolved(
                    replay.cubeState(),
                    F2LGeometry.targetSlotFor(step.targetSlot(), step.orientationBefore().toOrientation())
            ));
            for (var preservedSlot : step.preservedSlots().slots()) {
                assertTrue(F2LGeometry.isTargetSlotSolved(
                        replay.cubeState(),
                        F2LGeometry.targetSlotFor(preservedSlot, step.orientationBefore().toOrientation())
                ));
            }
        }

        assertTrue(F2LAnalyzer.isF2LSolved(replay.cubeState(), replay.orientation()));
    }

    private static CubeStateSnapshot cubeSnapshot(OrientedCube cube) {
        return CubeStateSnapshot.from(cube.cubeState());
    }
}
