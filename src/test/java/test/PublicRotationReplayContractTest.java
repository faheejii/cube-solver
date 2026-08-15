package test;

import cfop.CrossAnalyzer;
import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.CrossSolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for algorithms returned to the UI and cubing.js.
 *
 * <p>A returned algorithm is allowed to change the visible frame. The public
 * notation is replayed as physical moves, then independently canonicalized
 * back out of that visible frame before it is compared with the solver's
 * internal state. This intentionally does not use
 * {@code MoveApplier.executeMoves} as the public replay oracle.</p>
 */
class PublicRotationReplayContractTest {
    private static final String SCRAMBLE = "U R2 B2 R' B R D F B' U2 D2 R2 B U2 R2 U2 L2 F' U2 L";

    @Test
    void selectedCrossKeepsTheVisibleOrientationPrefix() {
        var solver = new CrossSolver();

        assertEquals("", solver.solve(new CubeState(), Face.D).toString());
        assertEquals("z2", solver.solve(new CubeState(), Face.U).toString());
        assertEquals("z", solver.solve(new CubeState(), Face.R).toString());
        assertEquals("z'", solver.solve(new CubeState(), Face.L).toString());
        assertEquals("x'", solver.solve(new CubeState(), Face.F).toString());
        assertEquals("x", solver.solve(new CubeState(), Face.B).toString());
    }

    @Test
    void publicReplayMatchesTheInternalOrientedCubeForEveryCrossFace() {
        for (var crossFace : Face.values()) {
            var scrambled = new CubeState();
            MoveApplier.applyAlgorithm(scrambled, SCRAMBLE);
            var solution = new CrossSolver().solve(scrambled, crossFace);

            var replayed = scrambled.copy();
            MoveApplier.applyMoves(replayed, solution.getMoves());

            var internal = new OrientedCube(scrambled.copy());
            internal.applyMoves(solution.getMoves());

            canonicalizeVisibleFrame(replayed, internal.orientation());
            assertSameState(internal.cubeState(), replayed, crossFace + " raw replay state");
            assertTrue(CrossAnalyzer.isCrossSolved(
                    replayed,
                    internal.orientation()
            ), crossFace + " cross after " + solution);
        }
    }

    @Test
    void publicReplayPreservesTheFrameAcrossCrossAndSubsequentLogicalMoves() {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, SCRAMBLE);

        var cross = new CrossSolver().solve(scrambled, Face.L);
        var publicSequence = cross.concat(Algorithm.parse("U'"));

        var replayed = scrambled.copy();
        MoveApplier.applyMoves(replayed, publicSequence.getMoves());

        var internal = new OrientedCube(scrambled.copy());
        internal.applyMoves(publicSequence.getMoves());

        canonicalizeVisibleFrame(replayed, internal.orientation());
        assertSameState(internal.cubeState(), replayed, "L-frame raw continuation");
        assertEquals(Face.L, internal.orientation().faceAt(Face.D));
    }

    @Test
    void zAndZPrimePublicReplay_preserveStickerStateAcrossFrameChanges() {
        for (var rotation : List.of(Move.Z, Move.Z_PRIME)) {
            var publicSequence = Algorithm.parse(rotation + " y R U' F2 L' D B2");
            var raw = new CubeState();
            MoveApplier.applyMoves(raw, publicSequence.getMoves());

            var internal = new OrientedCube();
            internal.applyMoves(publicSequence.getMoves());

            canonicalizeVisibleFrame(raw, internal.orientation());
            assertSameState(internal.cubeState(), raw, rotation + " raw sticker replay");
        }
    }

    /**
     * The raw public replay has the cube physically turned. Find the inverse
     * of that frame using only the 24 orientation keys, then apply those
     * rotations to the raw cube. This is a frame canonicalizer, not a second
     * move executor or a virtual oriented-cube oracle.
     */
    private static void canonicalizeVisibleFrame(CubeState raw, cube.CubeOrientation frame) {
        var inverse = inverseRotationPath(frame);
        MoveApplier.applyMoves(raw, inverse);
    }

    private static List<Move> inverseRotationPath(cube.CubeOrientation frame) {
        var start = cube.CubeOrientationKey.from(frame);
        var identity = cube.CubeOrientationKey.from(new cube.CubeOrientation());
        var queue = new ArrayDeque<OrientationPath>();
        var visited = new HashSet<cube.CubeOrientationKey>();
        queue.add(new OrientationPath(start, List.of()));
        visited.add(start);

        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            if (current.key().equals(identity)) {
                return current.moves();
            }
            for (var rotation : List.of(Move.X, Move.Y, Move.Z)) {
                var next = current.orientation();
                next.applyRotation(rotation);
                var key = cube.CubeOrientationKey.from(next);
                if (visited.add(key)) {
                    var moves = new ArrayList<>(current.moves());
                    moves.add(rotation);
                    queue.addLast(new OrientationPath(key, moves));
                }
            }
        }
        throw new AssertionError("Could not canonicalize frame " + frame);
    }

    private record OrientationPath(cube.CubeOrientationKey key, List<Move> moves) {
        private cube.CubeOrientation orientation() {
            return key.toOrientation();
        }
    }

    private static void assertSameState(CubeState expected, CubeState actual, String message) {
        assertArrayEquals(expected.cornerPerm, actual.cornerPerm, message + " corner permutation");
        assertArrayEquals(expected.cornerOri, actual.cornerOri, message + " corner orientation");
        assertArrayEquals(expected.edgePerm, actual.edgePerm, message + " edge permutation");
        assertArrayEquals(expected.edgeOri, actual.edgeOri, message + " edge orientation");
    }
}
