package solver.pruning;

import cfop.F2LSlot;
import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Move;
import cube.MoveApplier;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Developer CLI: reverse-BFS the canonical D-cross + FR-pair coordinate.
 * Run after compiling with {@code mvn -q -DskipTests package}, then use
 * {@code java -Xmx1g -cp target/classes solver.pruning.GenerateCombinedPruningTable}
 * (or provide an output path). The search uses a 73M-entry byte distance array
 * and a primitive int queue; peak heap is roughly 400 MiB. Generation is an
 * offline operation; generation took about 23 seconds in the implementation environment
 * and varies with machine speed.
 */
public final class GenerateCombinedPruningTable {
    private static final int UNVISITED = 0xff;
    private static final Path DEFAULT_OUTPUT = Path.of("src/main/resources/solver/pruning/cross-fr.bin");

    private GenerateCombinedPruningTable() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("Usage: GenerateCombinedPruningTable [output-file]");
        var output = args.length == 1 ? Path.of(args[0]) : DEFAULT_OUTPUT;
        var started = System.nanoTime();
        System.out.printf("Generating %d-state cross+FR-pair table (~%.1f MiB packed asset); use -Xmx1g (about 400 MiB peak heap), may take tens of seconds or longer%n",
                CombinedPruningCoordinates.STATE_COUNT,
                ((CombinedPruningCoordinates.STATE_COUNT + 1) / 2.0) / (1024.0 * 1024.0));
        var distances = generate();
        var maximumDistance = maxDistance(distances);
        if (maximumDistance > 15) throw new IllegalStateException("Generated maximum distance " + maximumDistance + " exceeds packed nibble limit 15");
        writeAtomically(output, distances);
        var elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf("Wrote %s (%d bytes; max distance %d) in %.1f seconds%n",
                output.toAbsolutePath(), Files.size(output), maximumDistance, elapsedSeconds);
    }

    /** Package-private for focused generator verification; full generation is intentionally not a unit-test fixture. */
    static byte[] generate() {
        var coordinateCount = CombinedPruningCoordinates.STATE_COUNT;
        var distances = new byte[coordinateCount];
        Arrays.fill(distances, (byte) UNVISITED);
        var queue = new int[coordinateCount];
        var crossMoves = buildCrossTransitions();
        var cornerMoves = buildCornerTransitions();
        var edgeMoves = buildEdgeTransitions();
        var complementRanks = buildComplementRanks();
        var goal = CombinedPruningCoordinates.solvedIndex();
        var head = 0;
        var tail = 0;
        distances[goal] = 0;
        queue[tail++] = goal;

        while (head < tail) {
            var state = queue[head++];
            var distance = Byte.toUnsignedInt(distances[state]);
            if (distance >= 254) throw new IllegalStateException("Combined-table distance exceeds byte format");
            var edgeOrientation = state % 2;
            var quotient = state / 2;
            var edgeRank = quotient % CombinedPruningCoordinates.CROSS_EDGE_POSITIONS;
            quotient /= CombinedPruningCoordinates.CROSS_EDGE_POSITIONS;
            var cornerCoordinate = quotient % CombinedPruningCoordinates.CORNER_STATES;
            var crossIndex = quotient / CombinedPruningCoordinates.CORNER_STATES;
            var crossRank = crossIndex / 16;
            var actualEdgePosition = CombinedPruningCoordinates.complementPositionFromRank(crossRank, edgeRank);
            var edgeCoordinate = actualEdgePosition * 2 + edgeOrientation;
            var crossOffset = crossIndex * CombinedPruningCoordinates.MOVE_COUNT;
            var cornerOffset = cornerCoordinate * CombinedPruningCoordinates.MOVE_COUNT;
            var edgeOffset = edgeCoordinate * CombinedPruningCoordinates.MOVE_COUNT;
            var nextDistance = (byte) (distance + 1);
            for (var move = 0; move < CombinedPruningCoordinates.MOVE_COUNT; move++) {
                var nextCross = crossMoves[crossOffset + move];
                var nextCorner = Byte.toUnsignedInt(cornerMoves[cornerOffset + move]);
                var nextEdge = Byte.toUnsignedInt(edgeMoves[edgeOffset + move]);
                var nextEdgeRank = Byte.toUnsignedInt(complementRanks[(nextCross / 16) * 12 + nextEdge / 2]);
                if (nextEdgeRank >= 8) throw new IllegalStateException("Move produced overlapping combined edge coordinates");
                var nextState = (((nextCross * CombinedPruningCoordinates.CORNER_STATES + nextCorner)
                        * CombinedPruningCoordinates.CROSS_EDGE_POSITIONS + nextEdgeRank) * 2) + nextEdge % 2;
                if (distances[nextState] == (byte) UNVISITED) {
                    distances[nextState] = nextDistance;
                    queue[tail++] = nextState;
                }
            }
        }
        if (tail != coordinateCount) throw new IllegalStateException("Combined coordinate graph is incomplete: reached " + tail + " / " + coordinateCount);
        return distances;
    }

    private static int[] buildCrossTransitions() {
        var transitions = new int[CombinedPruningCoordinates.CROSS_STATES * CombinedPruningCoordinates.MOVE_COUNT];
        for (var index = 0; index < CombinedPruningCoordinates.CROSS_STATES; index++) {
            var state = crossRepresentative(index);
            var offset = index * CombinedPruningCoordinates.MOVE_COUNT;
            for (var move = 0; move < CombinedPruningCoordinates.MOVE_COUNT; move++) {
                var moved = state.copy();
                MoveApplier.applyMove(moved, CombinedPruningCoordinates.MOVES[move]);
                transitions[offset + move] = crossIndex(moved);
            }
        }
        return transitions;
    }

    private static byte[] buildCornerTransitions() {
        var transitions = new byte[CombinedPruningCoordinates.CORNER_STATES * CombinedPruningCoordinates.MOVE_COUNT];
        for (var coordinate = 0; coordinate < CombinedPruningCoordinates.CORNER_STATES; coordinate++) {
            var state = cornerRepresentative(coordinate);
            var offset = coordinate * CombinedPruningCoordinates.MOVE_COUNT;
            for (var move = 0; move < CombinedPruningCoordinates.MOVE_COUNT; move++) {
                var moved = state.copy();
                MoveApplier.applyMove(moved, CombinedPruningCoordinates.MOVES[move]);
                var position = find(moved.cornerPerm, Corner.DFR.ordinal());
                transitions[offset + move] = (byte) (position * 3 + moved.cornerOri[position]);
            }
        }
        return transitions;
    }

    private static byte[] buildEdgeTransitions() {
        var transitions = new byte[24 * CombinedPruningCoordinates.MOVE_COUNT];
        for (var coordinate = 0; coordinate < 24; coordinate++) {
            var state = edgeRepresentative(coordinate);
            var offset = coordinate * CombinedPruningCoordinates.MOVE_COUNT;
            for (var move = 0; move < CombinedPruningCoordinates.MOVE_COUNT; move++) {
                var moved = state.copy();
                MoveApplier.applyMove(moved, CombinedPruningCoordinates.MOVES[move]);
                var position = find(moved.edgePerm, Edge.FR.ordinal());
                transitions[offset + move] = (byte) (position * 2 + moved.edgeOri[position]);
            }
        }
        return transitions;
    }

    private static byte[] buildComplementRanks() {
        var ranks = new byte[11_880 * 12];
        for (var rank = 0; rank < 11_880; rank++) {
            var positions = CombinedPruningCoordinates.crossPositions(rank * 16);
            for (var edgePosition = 0; edgePosition < 12; edgePosition++) {
                var value = 0xff;
                try {
                    value = CombinedPruningCoordinates.complementRank(positions, edgePosition);
                } catch (IllegalArgumentException ignored) {
                    // Cross edge occupies the same position; that coordinate is not a valid FR-edge location.
                }
                ranks[rank * 12 + edgePosition] = (byte) value;
            }
        }
        return ranks;
    }

    private static CubeState crossRepresentative(int index) {
        var state = new CubeState();
        var positions = CombinedPruningCoordinates.crossPositions(index);
        var bits = CombinedPruningCoordinates.crossOrientationBits(index);
        var edgePerm = new byte[12];
        var edgeOri = new byte[12];
        Arrays.fill(edgePerm, (byte) -1);
        var tracked = new int[]{Edge.DF.ordinal(), Edge.DR.ordinal(), Edge.DB.ordinal(), Edge.DL.ordinal()};
        var usedPieces = new boolean[12];
        for (var i = 0; i < 4; i++) {
            edgePerm[positions[i]] = (byte) tracked[i];
            edgeOri[positions[i]] = (byte) ((bits >>> i) & 1);
            usedPieces[tracked[i]] = true;
        }
        var nextPiece = 0;
        for (var position = 0; position < 12; position++) if (edgePerm[position] == (byte) -1) {
            while (usedPieces[nextPiece]) nextPiece++;
            edgePerm[position] = (byte) nextPiece;
            usedPieces[nextPiece++] = true;
        }
        state.edgePerm = edgePerm;
        state.edgeOri = edgeOri;
        return state;
    }

    private static CubeState cornerRepresentative(int coordinate) {
        var position = coordinate / 3;
        var orientation = coordinate % 3;
        var state = new CubeState();
        state.cornerPerm[position] = (byte) Corner.DFR.ordinal();
        state.cornerOri[position] = (byte) orientation;
        var used = new boolean[8];
        used[Corner.DFR.ordinal()] = true;
        var next = 0;
        for (var slot = 0; slot < 8; slot++) if (slot != position) {
            while (used[next]) next++;
            state.cornerPerm[slot] = (byte) next;
            used[next++] = true;
        }
        return state;
    }

    private static CubeState edgeRepresentative(int coordinate) {
        var position = coordinate / 2;
        var orientation = coordinate % 2;
        var state = new CubeState();
        state.edgePerm[position] = (byte) Edge.FR.ordinal();
        state.edgeOri[position] = (byte) orientation;
        var used = new boolean[12];
        used[Edge.FR.ordinal()] = true;
        var next = 0;
        for (var slot = 0; slot < 12; slot++) if (slot != position) {
            while (used[next]) next++;
            state.edgePerm[slot] = (byte) next;
            used[next++] = true;
        }
        return state;
    }

    private static int crossIndex(CubeState state) {
        var positions = new int[]{find(state.edgePerm, Edge.DF.ordinal()), find(state.edgePerm, Edge.DR.ordinal()),
                find(state.edgePerm, Edge.DB.ordinal()), find(state.edgePerm, Edge.DL.ordinal())};
        var bits = 0;
        for (var i = 0; i < 4; i++) bits |= state.edgeOri[positions[i]] << i;
        return CombinedPruningCoordinates.crossIndex(positions, bits);
    }

    private static int find(byte[] permutation, int piece) {
        for (var i = 0; i < permutation.length; i++) if (Byte.toUnsignedInt(permutation[i]) == piece) return i;
        throw new IllegalStateException("Tracked piece is missing from representative state");
    }

    private static int maxDistance(byte[] distances) {
        var max = 0;
        for (byte distance : distances) max = Math.max(max, Byte.toUnsignedInt(distance));
        return max;
    }

    private static void writeAtomically(Path output, byte[] distances) throws IOException {
        var absolute = output.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        var temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        try {
            try (var stream = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                CombinedPruningTableCodec.write(stream, distances);
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
