package solver;

import cfop.F2LSlot;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import solver.pruning.CombinedPruningCoordinates;
import solver.pruning.CombinedPruningTableCodec;
import solver.pruning.CrossPruningCoordinates;
import solver.pruning.CrossPruningTableCodec;

import java.io.IOException;
import java.io.InputStream;

/** Runtime access to the bundled cross-only and cross-plus-FR-pair pattern databases. */
public final class PruningTables {
    private static final String COMBINED_RESOURCE = "/solver/pruning/cross-fr.bin";
    private static final String CROSS_RESOURCE = "/solver/pruning/cross-only.bin";
    private static final F2LSlot[] SLOTS = F2LSlot.values();
    private static volatile byte[] crossFrDistances;
    private static volatile byte[] crossDistances;

    private PruningTables() {
    }

    /** Exact abstract distance for D-cross plus the requested canonical F2L pair. */
    public static int combinedDistance(CubeState state, F2LSlot slot) {
        var coordinate = CombinedPruningCoordinates.index(state, slot);
        return CombinedPruningTableCodec.distanceAt(table(), coordinate);
    }

    /** Admissible cross-only distance for the specified face's four cross edges. */
    public static int crossDistance(CubeState state, Edge[] crossEdges) {
        var coordinate = CrossPruningCoordinates.index(state, crossEdges);
        var face = CrossPruningCoordinates.faceFor(crossEdges);
        return crossDistance(coordinate, face);
    }

    public static int crossDistance(int coordinate, Face face) {
        if (coordinate < 0 || coordinate >= CrossPruningCoordinates.STATE_COUNT || face == null) {
            throw new IllegalArgumentException("Invalid cross pruning lookup");
        }
        return Byte.toUnsignedInt(crossTable()[face.ordinal() * CrossPruningCoordinates.STATE_COUNT + coordinate]);
    }

    /** Returns exact relaxed distances to the four possible XCross goals, indexed by F2LSlot ordinal. */
    public static int[] combinedDistances(CubeState state) {
        var distances = new int[SLOTS.length];
        if (state == null) throw new IllegalArgumentException("Cube state is required");
        for (var slot : SLOTS) distances[slot.ordinal()] = combinedDistance(state, slot);
        return distances;
    }

    public static int xcrossLowerBound(CubeState state) {
        var distances = distancesPacked(state);
        var first = distances & 0x0f;
        var second = (distances >>> 4) & 0x0f;
        var third = (distances >>> 8) & 0x0f;
        var fourth = (distances >>> 12) & 0x0f;
        return Math.min(Math.min(first, second), Math.min(third, fourth));
    }

    /** Admissible lower bound for solving any two distinct cross-plus-pair goals. */
    public static int xxcrossLowerBound(CubeState state) {
        var distances = distancesPacked(state);
        var best = Integer.MAX_VALUE;
        for (var first = 0; first < 4; first++) {
            for (var second = first + 1; second < 4; second++) {
                var firstDistance = (distances >>> (first * 4)) & 0x0f;
                var secondDistance = (distances >>> (second * 4)) & 0x0f;
                best = Math.min(best, Math.max(firstDistance, secondDistance));
            }
        }
        return best;
    }

    private static int distancesPacked(CubeState state) {
        if (state == null) throw new IllegalArgumentException("Cube state is required");
        var table = table();
        var packed = 0;
        for (var slot : SLOTS) {
            var distance = CombinedPruningTableCodec.distanceAt(table,
                    CombinedPruningCoordinates.index(state, slot));
            packed |= distance << (slot.ordinal() * 4);
        }
        return packed;
    }

    private static byte[] table() {
        var distances = crossFrDistances;
        if (distances == null) {
            synchronized (PruningTables.class) {
                distances = crossFrDistances;
                if (distances == null) crossFrDistances = distances = load();
            }
        }
        return distances;
    }

    private static byte[] load() {
        try (InputStream input = PruningTables.class.getResourceAsStream(COMBINED_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing pruning table resource: " + COMBINED_RESOURCE);
            return CombinedPruningTableCodec.read(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load pruning table resource: " + COMBINED_RESOURCE, e);
        }
    }

    private static byte[] crossTable() {
        var distances = crossDistances;
        if (distances == null) {
            synchronized (PruningTables.class) {
                distances = crossDistances;
                if (distances == null) crossDistances = distances = loadCrossTable();
            }
        }
        return distances;
    }

    private static byte[] loadCrossTable() {
        try (InputStream input = PruningTables.class.getResourceAsStream(CROSS_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing pruning table resource: " + CROSS_RESOURCE);
            return CrossPruningTableCodec.read(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load pruning table resource: " + CROSS_RESOURCE, e);
        }
    }
}
