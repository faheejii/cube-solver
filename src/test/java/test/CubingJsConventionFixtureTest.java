package test;

import cube.CubeState;
import cube.Move;
import cube.MoveApplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Static cubing.js 0.63.3 convention fixtures. Values were generated from
 * cube3x3x3.kpuzzle().moveToTransformation(move).transformationData and then
 * translated once into this project's Corner and Edge enum order.
 */
class CubingJsConventionFixtureTest {
    @Test
    void basicFaceTurns_shouldMatchCubingJsPieceAndStickerOrientations() {
        assertMovesMatchCubingJs(Map.ofEntries(
                Map.entry(Move.U, fixture(
                        "3,0,1,2,4,5,6,7", "0,0,0,0,0,0,0,0",
                        "3,0,1,2,4,5,6,7,8,9,10,11", "0,0,0,0,0,0,0,0,0,0,0,0")),
                Map.entry(Move.R, fixture(
                        "4,1,2,0,7,5,6,3", "2,0,0,1,1,0,0,2",
                        "8,1,2,3,11,5,6,7,4,9,10,0", "0,0,0,0,0,0,0,0,0,0,0,0")),
                Map.entry(Move.F, fixture(
                        "1,5,2,3,0,4,6,7", "1,2,0,0,2,1,0,0",
                        "0,9,2,3,4,8,6,7,1,5,10,11", "0,1,0,0,0,1,0,0,1,1,0,0")),
                Map.entry(Move.D, fixture(
                        "0,1,2,3,5,6,7,4", "0,0,0,0,0,0,0,0",
                        "0,1,2,3,5,6,7,4,8,9,10,11", "0,0,0,0,0,0,0,0,0,0,0,0")),
                Map.entry(Move.L, fixture(
                        "0,2,6,3,4,1,5,7", "0,1,2,0,0,2,1,0",
                        "0,1,10,3,4,5,9,7,8,2,6,11", "0,0,0,0,0,0,0,0,0,0,0,0")),
                Map.entry(Move.B, fixture(
                        "0,1,3,7,4,5,2,6", "0,0,1,2,0,0,2,1",
                        "0,1,2,11,4,5,6,10,8,9,3,7", "0,0,0,1,0,0,0,1,0,0,1,1"))
        ));
    }

    @Test
    void wholeCubeRotations_shouldMatchCubingJsPieceAndStickerOrientations() {
        assertMovesMatchCubingJs(Map.ofEntries(
                Map.entry(Move.X, fixture(
                        "4,5,1,0,7,6,2,3", "2,1,2,1,1,2,1,2",
                        "8,5,9,1,11,7,10,3,4,6,2,0", "0,1,0,1,0,1,0,1,0,0,0,0")),
                Map.entry(Move.Y, fixture(
                        "3,0,1,2,7,4,5,6", "0,0,0,0,0,0,0,0",
                        "3,0,1,2,7,4,5,6,11,8,9,10", "0,0,0,0,0,0,0,0,1,1,1,1")),
                Map.entry(Move.Z, fixture(
                        "1,5,6,2,0,4,7,3", "1,2,1,2,2,1,2,1",
                        "2,9,6,10,0,8,4,11,1,5,7,3", "1,1,1,1,1,1,1,1,1,1,1,1"))
        ));
    }

    private static void assertMovesMatchCubingJs(Map<Move, Fixture> fixtures) {
        List<Executable> assertions = new ArrayList<>();
        for (var entry : new LinkedHashMap<>(fixtures).entrySet()) {
            var cube = new CubeState();
            MoveApplier.applyMove(cube, entry.getKey());
            var expected = entry.getValue();

            assertions.add(() -> assertArrayEquals(expected.cornerPerm(), cube.cornerPerm,
                    entry.getKey() + " corner permutation"));
            assertions.add(() -> assertArrayEquals(expected.cornerOri(), cube.cornerOri,
                    entry.getKey() + " corner orientation"));
            assertions.add(() -> assertArrayEquals(expected.edgePerm(), cube.edgePerm,
                    entry.getKey() + " edge permutation"));
            assertions.add(() -> assertArrayEquals(expected.edgeOri(), cube.edgeOri,
                    entry.getKey() + " edge orientation"));
        }
        assertAll(assertions);
    }

    private static Fixture fixture(String cornerPerm, String cornerOri, String edgePerm, String edgeOri) {
        return new Fixture(bytes(cornerPerm), bytes(cornerOri), bytes(edgePerm), bytes(edgeOri));
    }

    private static byte[] bytes(String values) {
        var parts = values.split(",");
        var result = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Byte.parseByte(parts[i]);
        }
        return result;
    }

    private record Fixture(byte[] cornerPerm, byte[] cornerOri, byte[] edgePerm, byte[] edgeOri) {
    }
}
