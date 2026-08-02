package test;

import cfop.F2LAnalyzer;
import cfop.F2LGeometry;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.CubeStateSnapshot;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.F2LSolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LOptimizedTraceTest {
    @Test
    void optimizedCandidates_shouldCarryIndependentReplayablePairTraces() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R U R' U'");

        var postCrossCube = cube.copy();
        var candidates = new F2LSolver().solveOptimizedCandidates(
                new OrientedCube(postCrossCube.copy()),
                ignored -> {
                }
        );

        assertTrue(!candidates.isEmpty());
        for (int index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            var trace = candidate.trace();
            assertNotNull(trace);
            assertTrue(trace.solved());
            assertTrue(trace.algorithmMatchesPairSteps());
            assertEquals(candidate.algorithm().toString(), trace.algorithm().toString());

            if (index > 0) {
                assertNotSame(candidates.get(index - 1).trace(), trace);
                assertNotSame(candidates.get(index - 1).trace().pairSteps(), trace.pairSteps());
            }

            var replay = new OrientedCube(postCrossCube.copy());
            for (var step : trace.pairSteps()) {
                assertEquals(step.stateBefore(), CubeStateSnapshot.from(replay.cubeState()));
                assertEquals(step.orientationBefore(), CubeOrientationKey.from(replay.orientation()));

                replay.applyMoves(step.completeMoves());

                assertEquals(step.stateAfter(), CubeStateSnapshot.from(replay.cubeState()));
                assertEquals(step.orientationAfter(), CubeOrientationKey.from(replay.orientation()));
                assertTrue(F2LGeometry.isTargetSlotSolved(
                        replay.cubeState(),
                        F2LGeometry.targetSlotFor(
                                step.targetSlot(), step.orientationBefore().toOrientation()
                        )
                ));
                for (var preservedSlot : step.preservedSlots().slots()) {
                    assertTrue(F2LGeometry.isTargetSlotSolved(
                            replay.cubeState(),
                            F2LGeometry.targetSlotFor(
                                    preservedSlot, step.orientationBefore().toOrientation()
                            )
                    ));
                }
            }

            assertEquals(CubeStateSnapshot.from(candidate.cube()), CubeStateSnapshot.from(replay.cubeState()));
            assertEquals(
                    CubeOrientationKey.from(candidate.orientation()),
                    CubeOrientationKey.from(replay.orientation())
            );
            assertTrue(F2LAnalyzer.isF2LSolved(replay.cubeState(), replay.orientation()));
        }
    }
}
