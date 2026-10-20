package solver.pruning;

import cfop.F2LSlot;
import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Move;
import cube.MoveApplier;

/** Coordinate for the four D-cross edges plus one canonical F2L pair. */
public final class CombinedPruningCoordinates {
    public static final int CROSS_STATES = 190_080;
    public static final int CORNER_STATES = 24;
    public static final int CROSS_EDGE_POSITIONS = 8;
    public static final int EDGE_STATES = 16;
    public static final int STATE_COUNT = CROSS_STATES * CORNER_STATES * CROSS_EDGE_POSITIONS * 2;
    public static final int MOVE_COUNT = 18;
    private static final F2LSlot[] SLOTS = F2LSlot.values();
    public static final Move[] MOVES = {
            Move.U, Move.U2, Move.U_PRIME, Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME, Move.D, Move.D2, Move.D_PRIME,
            Move.L, Move.L2, Move.L_PRIME, Move.B, Move.B2, Move.B_PRIME
    };

    private static final int[][] CROSS_PERMUTATIONS = new int[11_880][4];
    private static final int[][][][] CROSS_RANK = new int[12][12][12][12];
    private static final int[] CROSS_PIECES = {Edge.DF.ordinal(), Edge.DR.ordinal(), Edge.DB.ordinal(), Edge.DL.ordinal()};
    private static final int[] Y_CORNER_LABEL = new int[8];
    private static final int[] Y_EDGE_LABEL = new int[12];
    private static final int[] Y_CORNER_ORIENTATION = new int[8];
    private static final int[] Y_EDGE_ORIENTATION = new int[12];

    static {
        for (var a = 0; a < 12; a++) for (var b = 0; b < 12; b++)
            for (var c = 0; c < 12; c++) for (var d = 0; d < 12; d++) CROSS_RANK[a][b][c][d] = -1;
        var rank = 0;
        for (var a = 0; a < 12; a++) for (var b = 0; b < 12; b++) if (b != a)
            for (var c = 0; c < 12; c++) if (c != a && c != b)
                for (var d = 0; d < 12; d++) if (d != a && d != b && d != c) {
                    CROSS_PERMUTATIONS[rank][0] = a;
                    CROSS_PERMUTATIONS[rank][1] = b;
                    CROSS_PERMUTATIONS[rank][2] = c;
                    CROSS_PERMUTATIONS[rank][3] = d;
                    CROSS_RANK[a][b][c][d] = rank++;
                }
        var rotatedSolved = new CubeState();
        MoveApplier.applyMove(rotatedSolved, Move.Y);
        for (var position = 0; position < 8; position++) {
            Y_CORNER_LABEL[Byte.toUnsignedInt(rotatedSolved.cornerPerm[position])] = position;
            Y_CORNER_ORIENTATION[position] = Byte.toUnsignedInt(rotatedSolved.cornerOri[position]);
        }
        for (var position = 0; position < 12; position++) {
            Y_EDGE_LABEL[Byte.toUnsignedInt(rotatedSolved.edgePerm[position])] = position;
            Y_EDGE_ORIENTATION[position] = Byte.toUnsignedInt(rotatedSolved.edgeOri[position]);
        }
    }

    private CombinedPruningCoordinates() {
    }

    public static int index(CubeState state, F2LSlot slot) {
        requireState(state);
        if (slot == null) throw new IllegalArgumentException("F2L slot is required");
        return indexFrontRight(rotateToFrontRight(state, slot));
    }

    /** Coordinate lookup for an already-normalized FR target slot; avoids a redundant state copy. */
    public static int indexFrontRight(CubeState normalized) {
        requireState(normalized);
        var crossPositions = crossPositionsForPieces(normalized);
        var crossOri = crossOrientationBits(normalized, crossPositions);
        var crossIndex = crossIndex(crossPositions, crossOri);
        var cornerPosition = find(normalized.cornerPerm, Corner.DFR.ordinal(), "FR pair corner");
        var cornerOri = orientation(normalized.cornerOri[cornerPosition], 3, "corner");
        var edgePosition = find(normalized.edgePerm, Edge.FR.ordinal(), "FR pair edge");
        var edgeOri = orientation(normalized.edgeOri[edgePosition], 2, "edge");
        var edgeRank = complementRank(crossPositions, edgePosition);
        return encode(crossIndex, cornerPosition * 3 + cornerOri, edgeRank, edgeOri);
    }

    public static int solvedIndex() {
        return index(new CubeState(), F2LSlot.FR);
    }

    public static int encode(int crossIndex, int cornerCoordinate, int edgeRank, int edgeOrientation) {
        if (crossIndex < 0 || crossIndex >= CROSS_STATES || cornerCoordinate < 0 || cornerCoordinate >= CORNER_STATES
                || edgeRank < 0 || edgeRank >= CROSS_EDGE_POSITIONS || edgeOrientation < 0 || edgeOrientation > 1) {
            throw new IllegalArgumentException("Invalid combined pruning coordinate");
        }
        return (((crossIndex * CORNER_STATES + cornerCoordinate) * CROSS_EDGE_POSITIONS + edgeRank) * 2)
                + edgeOrientation;
    }

    public static int crossIndex(int[] positions, int orientationBits) {
        var rank = CROSS_RANK[positions[0]][positions[1]][positions[2]][positions[3]];
        if (rank < 0 || orientationBits < 0 || orientationBits > 15) throw new IllegalArgumentException("Invalid cross coordinate");
        return rank * 16 + orientationBits;
    }

    public static int[] crossPositions(int crossIndex) {
        check(crossIndex, CROSS_STATES, "cross");
        return CROSS_PERMUTATIONS[crossIndex / 16].clone();
    }

    public static int crossOrientationBits(int crossIndex) {
        check(crossIndex, CROSS_STATES, "cross");
        return crossIndex % 16;
    }

    public static int complementPosition(int[] crossPositions, int rank) {
        if (rank < 0 || rank >= CROSS_EDGE_POSITIONS) throw new IllegalArgumentException("Invalid complementary edge rank");
        var seen = 0;
        for (var position = 0; position < 12; position++) {
            if (!contains(crossPositions, position) && seen++ == rank) return position;
        }
        throw new IllegalArgumentException("Invalid cross position coordinate");
    }

    public static int complementPositionFromRank(int crossPermutationRank, int rank) {
        check(crossPermutationRank, CROSS_PERMUTATIONS.length, "cross permutation");
        if (rank < 0 || rank >= CROSS_EDGE_POSITIONS) throw new IllegalArgumentException("Invalid complementary edge rank");
        var seen = 0;
        var positions = CROSS_PERMUTATIONS[crossPermutationRank];
        for (var position = 0; position < 12; position++) {
            if (!contains(positions, position) && seen++ == rank) return position;
        }
        throw new IllegalArgumentException("Invalid cross permutation coordinate");
    }

    public static int crossPositionAt(int crossIndex, int trackedPieceIndex) {
        check(crossIndex, CROSS_STATES, "cross");
        if (trackedPieceIndex < 0 || trackedPieceIndex >= 4) throw new IllegalArgumentException("Invalid tracked cross-edge index");
        return CROSS_PERMUTATIONS[crossIndex / 16][trackedPieceIndex];
    }

    public static F2LSlot rotateSlotY(F2LSlot slot) {
        if (slot == null) throw new IllegalArgumentException("F2L slot is required");
        var cornerHome = Y_CORNER_LABEL[slot.corner().ordinal()];
        var edgeHome = Y_EDGE_LABEL[slot.edge().ordinal()];
        for (var candidate : SLOTS) if (candidate.corner().ordinal() == cornerHome && candidate.edge().ordinal() == edgeHome) return candidate;
        throw new IllegalStateException("Y rotation did not map to a canonical F2L slot: " + slot);
    }

    public static int complementRank(int[] crossPositions, int edgePosition) {
        if (contains(crossPositions, edgePosition)) throw new IllegalArgumentException("Tracked F2L edge overlaps a cross edge");
        var rank = 0;
        for (var position = 0; position < edgePosition; position++) if (!contains(crossPositions, position)) rank++;
        if (edgePosition < 0 || edgePosition >= 12) throw new IllegalArgumentException("Invalid edge position");
        return rank;
    }

    public static int[] decode(int index) {
        check(index, STATE_COUNT, "combined");
        var edgeOrientation = index % 2;
        var quotient = index / 2;
        var edgeRank = quotient % CROSS_EDGE_POSITIONS;
        quotient /= CROSS_EDGE_POSITIONS;
        var cornerCoordinate = quotient % CORNER_STATES;
        var crossIndex = quotient / CORNER_STATES;
        return new int[]{crossIndex, cornerCoordinate, edgeRank, edgeOrientation};
    }

    /** Rotates/relabels a state as a change of D-axis reference frame; solved remains solved. */
    public static CubeState rotateFrameY(CubeState state) {
        requireState(state);
        var rotated = state.copy();
        rotateFrameYInPlace(rotated);
        return rotated;
    }

    /** Applies the same logical Y-frame normalization in place. */
    public static void rotateFrameYInPlace(CubeState rotated) {
        requireState(rotated);
        MoveApplier.applyMove(rotated, Move.Y);
        for (var position = 0; position < 8; position++) {
            rotated.cornerPerm[position] = (byte) Y_CORNER_LABEL[Byte.toUnsignedInt(rotated.cornerPerm[position])];
            rotated.cornerOri[position] = (byte) Math.floorMod(rotated.cornerOri[position] - Y_CORNER_ORIENTATION[position], 3);
        }
        for (var position = 0; position < 12; position++) {
            rotated.edgePerm[position] = (byte) Y_EDGE_LABEL[Byte.toUnsignedInt(rotated.edgePerm[position])];
            rotated.edgeOri[position] = (byte) Math.floorMod(rotated.edgeOri[position] - Y_EDGE_ORIENTATION[position], 2);
        }
    }

    private static CubeState rotateToFrontRight(CubeState state, F2LSlot slot) {
        var rotated = state.copy();
        var currentSlot = slot;
        for (var turns = 0; turns < 4; turns++) {
            if (currentSlot == F2LSlot.FR) return rotated;
            rotateFrameYInPlace(rotated);
            currentSlot = slotAfterY(currentSlot);
        }
        throw new IllegalStateException("D-axis rotation did not map the F2L slot to FR: " + slot);
    }

    private static F2LSlot slotAfterY(F2LSlot slot) {
        return rotateSlotY(slot);
    }

    private static int[] crossPositionsForPieces(CubeState state) {
        var positions = new int[4];
        for (var i = 0; i < 4; i++) positions[i] = find(state.edgePerm, CROSS_PIECES[i], "D-cross edge");
        return positions;
    }

    private static int crossOrientationBits(CubeState state, int[] positions) {
        var bits = 0;
        for (var i = 0; i < 4; i++) bits |= orientation(state.edgeOri[positions[i]], 2, "cross edge") << i;
        return bits;
    }

    private static int orientation(byte value, int modulus, String label) {
        var orientation = Byte.toUnsignedInt(value);
        if (orientation >= modulus) throw new IllegalArgumentException("Invalid " + label + " orientation");
        return orientation;
    }

    private static int find(byte[] permutation, int piece, String label) {
        var found = -1;
        for (var position = 0; position < permutation.length; position++) if (Byte.toUnsignedInt(permutation[position]) == piece) {
            if (found >= 0) throw new IllegalArgumentException("Duplicate " + label);
            found = position;
        }
        if (found < 0) throw new IllegalArgumentException("Missing " + label);
        return found;
    }

    private static boolean contains(int[] values, int sought) {
        for (var value : values) if (value == sought) return true;
        return false;
    }

    private static void requireState(CubeState state) {
        if (state == null || state.cornerPerm == null || state.cornerOri == null || state.edgePerm == null || state.edgeOri == null
                || state.cornerPerm.length != 8 || state.cornerOri.length != 8 || state.edgePerm.length != 12 || state.edgeOri.length != 12) {
            throw new IllegalArgumentException("A complete CubeState is required");
        }
    }

    private static void check(int index, int size, String label) {
        if (index < 0 || index >= size) throw new IllegalArgumentException("Invalid " + label + " coordinate: " + index);
    }
}
