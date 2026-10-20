package solver.pruning;

import cube.Face;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Offline reverse-BFS generator for the six cross-only pattern databases. */
public final class GenerateCrossPruningTable {
    private static final int UNVISITED = 0xff;
    private static final Path DEFAULT_OUTPUT = Path.of("src/main/resources/solver/pruning/cross-only.bin");

    private GenerateCrossPruningTable() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("Usage: GenerateCrossPruningTable [output-file]");
        var output = args.length == 1 ? Path.of(args[0]) : DEFAULT_OUTPUT;
        var started = System.nanoTime();
        var distances = generate();
        var maximumDistance = maxDistance(distances);
        if (maximumDistance > 15) throw new IllegalStateException("Generated distance exceeds the supported limit: " + maximumDistance);
        writeAtomically(output, distances);
        System.out.printf("Wrote %s (%d bytes; maximum distance %d) in %.2f seconds%n",
                output.toAbsolutePath(), Files.size(output), maximumDistance,
                (System.nanoTime() - started) / 1_000_000_000.0);
    }

    static byte[] generate() {
        var transitions = buildTransitions();
        var stateCount = CrossPruningCoordinates.STATE_COUNT;
        var distances = new byte[CrossPruningCoordinates.FACE_COUNT * stateCount];
        var queue = new int[stateCount];
        for (var face : Face.values()) {
            var offset = face.ordinal() * stateCount;
            Arrays.fill(distances, offset, offset + stateCount, (byte) UNVISITED);
            var head = 0;
            var tail = 0;
            var goal = CrossPruningCoordinates.solvedIndex(face);
            distances[offset + goal] = 0;
            queue[tail++] = goal;
            while (head < tail) {
                var state = queue[head++];
                var nextDistance = (byte) (Byte.toUnsignedInt(distances[offset + state]) + 1);
                var transitionOffset = state * CrossPruningCoordinates.MOVES.length;
                for (var move = 0; move < CrossPruningCoordinates.MOVES.length; move++) {
                    var next = transitions[transitionOffset + move];
                    if (distances[offset + next] == (byte) UNVISITED) {
                        distances[offset + next] = nextDistance;
                        queue[tail++] = next;
                    }
                }
            }
            if (tail != stateCount) {
                throw new IllegalStateException("Cross coordinate graph for " + face + " is incomplete: " + tail + "/" + stateCount);
            }
        }
        return distances;
    }

    private static int[] buildTransitions() {
        var stateCount = CrossPruningCoordinates.STATE_COUNT;
        var moveCount = CrossPruningCoordinates.MOVES.length;
        var transitions = new int[stateCount * moveCount];
        for (var state = 0; state < stateCount; state++) {
            var transitionOffset = state * moveCount;
            for (var move = 0; move < moveCount; move++) {
                transitions[transitionOffset + move] = CrossPruningCoordinates.transition(
                        state, CrossPruningCoordinates.MOVES[move]);
            }
        }
        return transitions;
    }

    private static int maxDistance(byte[] distances) {
        var maximum = 0;
        for (byte distance : distances) maximum = Math.max(maximum, Byte.toUnsignedInt(distance));
        return maximum;
    }

    private static void writeAtomically(Path output, byte[] distances) throws IOException {
        var parent = output.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, output.getFileName().toString(), ".tmp");
        try {
            try (var stream = Files.newOutputStream(temporary)) {
                CrossPruningTableCodec.write(stream, distances);
            }
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicMoveFailure) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
