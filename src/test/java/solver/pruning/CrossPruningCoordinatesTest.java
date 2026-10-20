package solver.pruning;

import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
import solver.PruningTables;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossPruningCoordinatesTest {
    @Test
    void moveTransitionsMatchTheCubieModelForEveryFaceAndTurn() {
        var state = new CubeState();
        MoveApplier.applyAlgorithm(state, "R U F' L2 D B' U2 R' F D2");
        for (var face : Face.values()) {
            var pieces = edgesFor(face);
            var coordinate = CrossPruningCoordinates.index(state, pieces);
            for (var move : CrossPruningCoordinates.MOVES) {
                var expected = state.copy();
                MoveApplier.applyMove(expected, move);
                assertEquals(CrossPruningCoordinates.index(expected, pieces),
                        CrossPruningCoordinates.transition(coordinate, move),
                        face + " coordinate after " + move);
            }
        }
    }

    @Test
    void bundledTablesHaveZeroAtEveryFaceGoalAndBoundShallowScrambles() {
        for (var face : Face.values()) {
            var pieces = edgesFor(face);
            var solved = new CubeState();
            var goal = CrossPruningCoordinates.solvedIndex(face);
            assertEquals(0, PruningTables.crossDistance(goal, face));
            assertEquals(0, PruningTables.crossDistance(solved, pieces));

            var oneMove = solved.copy();
            MoveApplier.applyMove(oneMove, Move.R);
            assertTrue(PruningTables.crossDistance(oneMove, pieces) <= 1);
            var twoMoves = oneMove.copy();
            MoveApplier.applyMove(twoMoves, Move.F2);
            assertTrue(PruningTables.crossDistance(twoMoves, pieces) <= 2);
        }
    }

    @Test
    void tableCodecValidatesPayloadAndChecksum() throws Exception {
        var distances = new byte[CrossPruningCoordinates.FACE_COUNT * CrossPruningCoordinates.STATE_COUNT];
        var encoded = CrossPruningTableCodec.encode(distances);
        assertTrue(Arrays.equals(distances, CrossPruningTableCodec.read(new ByteArrayInputStream(encoded))));
        encoded[encoded.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> CrossPruningTableCodec.read(new ByteArrayInputStream(encoded)));
    }

    @Test
    void generatedDistancesMatchTheBundledRuntimeAsset() throws Exception {
        byte[] bundled;
        try (InputStream input = getClass().getResourceAsStream("/solver/pruning/cross-only.bin")) {
            bundled = CrossPruningTableCodec.read(input);
        }
        assertArrayEquals(bundled, GenerateCrossPruningTable.generate());
    }

    private static Edge[] edgesFor(Face face) {
        return Arrays.stream(Edge.values()).filter(edge -> touches(edge, face)).toArray(Edge[]::new);
    }

    private static boolean touches(Edge edge, Face face) {
        return switch (edge) {
            case UR -> face == Face.U || face == Face.R;
            case UF -> face == Face.U || face == Face.F;
            case UL -> face == Face.U || face == Face.L;
            case UB -> face == Face.U || face == Face.B;
            case DR -> face == Face.D || face == Face.R;
            case DF -> face == Face.D || face == Face.F;
            case DL -> face == Face.D || face == Face.L;
            case DB -> face == Face.D || face == Face.B;
            case FR -> face == Face.F || face == Face.R;
            case FL -> face == Face.F || face == Face.L;
            case BL -> face == Face.B || face == Face.L;
            case BR -> face == Face.B || face == Face.R;
        };
    }
}
