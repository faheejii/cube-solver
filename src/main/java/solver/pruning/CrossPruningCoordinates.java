package solver.pruning;

import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;

import java.util.Arrays;

/** Four-edge cross coordinates and their face-turn transition maps. */
public final class CrossPruningCoordinates {
    public static final int STATE_COUNT = CombinedPruningCoordinates.CROSS_STATES;
    public static final int FACE_COUNT = 6;
    public static final Move[] MOVES = CombinedPruningCoordinates.MOVES;
    private static final int[][] EDGE_TRANSITIONS = buildEdgeTransitions();

    private CrossPruningCoordinates() {
    }

    public static int index(CubeState state, Edge[] crossPieces) {
        if (state == null || crossPieces == null || crossPieces.length != 4) {
            throw new IllegalArgumentException("Cube state and exactly four cross edges are required");
        }
        var pieces = Arrays.stream(crossPieces).mapToInt(Edge::ordinal).sorted().toArray();
        return CombinedPruningCoordinates.crossIndex(state, pieces);
    }

    public static int solvedIndex(Face face) {
        var positions = goalPositions(face);
        return CombinedPruningCoordinates.crossIndex(positions, 0);
    }

    public static Face faceFor(Edge[] crossPieces) {
        if (crossPieces == null || crossPieces.length != 4) {
            throw new IllegalArgumentException("Exactly four cross edges are required");
        }
        for (var face : Face.values()) {
            var matches = 0;
            for (var edge : crossPieces) if (hasFace(edge, face)) matches++;
            if (matches == 4) return face;
        }
        throw new IllegalArgumentException("Edges do not form a face cross");
    }

    public static int transition(int coordinate, Move move) {
        if (coordinate < 0 || coordinate >= STATE_COUNT || move == null || move.ordinal() >= MOVES.length) {
            throw new IllegalArgumentException("Invalid cross transition");
        }
        var orientations = CombinedPruningCoordinates.crossOrientationBits(coordinate);
        var moveIndex = move.ordinal();
        var first = EDGE_TRANSITIONS[moveIndex][CombinedPruningCoordinates.crossPositionAt(coordinate, 0) * 2 + (orientations & 1)];
        var second = EDGE_TRANSITIONS[moveIndex][CombinedPruningCoordinates.crossPositionAt(coordinate, 1) * 2 + ((orientations >>> 1) & 1)];
        var third = EDGE_TRANSITIONS[moveIndex][CombinedPruningCoordinates.crossPositionAt(coordinate, 2) * 2 + ((orientations >>> 2) & 1)];
        var fourth = EDGE_TRANSITIONS[moveIndex][CombinedPruningCoordinates.crossPositionAt(coordinate, 3) * 2 + ((orientations >>> 3) & 1)];
        var nextOrientations = (first & 1) | ((second & 1) << 1) | ((third & 1) << 2) | ((fourth & 1) << 3);
        return CombinedPruningCoordinates.crossIndex(first / 2, second / 2, third / 2, fourth / 2, nextOrientations);
    }

    static int[] goalPositions(Face face) {
        if (face == null) throw new IllegalArgumentException("Cross face is required");
        var positions = new int[4];
        var count = 0;
        for (var edge : Edge.values()) if (hasFace(edge, face)) positions[count++] = edge.ordinal();
        if (count != 4) throw new IllegalStateException("Expected four edges around " + face);
        Arrays.sort(positions);
        return positions;
    }

    private static int[][] buildEdgeTransitions() {
        var transitions = new int[MOVES.length][24];
        for (var moveIndex = 0; moveIndex < MOVES.length; moveIndex++) {
            for (var position = 0; position < 12; position++) {
                for (var orientation = 0; orientation < 2; orientation++) {
                    var cube = representativeEdge(position, orientation);
                    MoveApplier.applyMove(cube, MOVES[moveIndex]);
                    var nextPosition = find(cube.edgePerm, Edge.DF.ordinal());
                    transitions[moveIndex][position * 2 + orientation] =
                            nextPosition * 2 + Byte.toUnsignedInt(cube.edgeOri[nextPosition]);
                }
            }
        }
        return transitions;
    }

    private static CubeState representativeEdge(int position, int orientation) {
        var state = new CubeState();
        state.edgePerm[position] = (byte) Edge.DF.ordinal();
        state.edgeOri[position] = (byte) orientation;
        var used = new boolean[12];
        used[Edge.DF.ordinal()] = true;
        var next = 0;
        for (var slot = 0; slot < 12; slot++) if (slot != position) {
            while (used[next]) next++;
            state.edgePerm[slot] = (byte) next;
            used[next++] = true;
        }
        return state;
    }

    private static int find(byte[] permutation, int piece) {
        for (var position = 0; position < permutation.length; position++) {
            if (Byte.toUnsignedInt(permutation[position]) == piece) return position;
        }
        throw new IllegalStateException("Tracked edge disappeared during move");
    }

    private static boolean hasFace(Edge edge, Face face) {
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
