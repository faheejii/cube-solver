package solver;

import java.util.HashMap;
import java.util.Map;

import cfop.F2LSlot;
import cube.Algorithm;
import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.LogicalFaceletView;
import cube.Move;
import cube.MoveApplier;
import cube.OrientationFrames;
import io.CubeConverter;
import io.FaceletState;

/** Searches for a cross with two or one F2L pairs before normal cross fallback. */
final class AdvancedCrossSearch {
    private static final int XXCROSS_MAX_DEPTH = 10;
    private static final int CROSS_MAX_DEPTH = 8;
    private static final F2LSlot[] SLOTS = F2LSlot.values();
    private static final Move[] FACE_TURNS = {
            Move.U, Move.U2, Move.U_PRIME,
            Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME,
            Move.D, Move.D2, Move.D_PRIME,
            Move.L, Move.L2, Move.L_PRIME,
            Move.B, Move.B2, Move.B_PRIME
    };
    private static final Edge[] D_CROSS = {Edge.DF, Edge.DR, Edge.DB, Edge.DL};
    private static final Edge[] TRACKED_EDGES = {
            Edge.DF, Edge.DR, Edge.DB, Edge.DL, Edge.FR, Edge.FL, Edge.BL, Edge.BR
    };
    private static final Corner[] TRACKED_CORNERS = {
            Corner.DFR, Corner.DLF, Corner.DBL, Corner.DRB
    };

    private AdvancedCrossSearch() {
    }

    static SearchResult solve(CubeState input, Face crossFace) {
        var prefix = OrientationFrames.orientationToD(crossFace);
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        var normalized = normalizeToFrame(input, orientation);

        var xxcross = search(normalized, true, XXCROSS_MAX_DEPTH);
        if (xxcross != null) {
            return new SearchResult(prefix.concat(xxcross), Goal.XXCROSS);
        }

        var xcross = search(normalized, false, CROSS_MAX_DEPTH);
        if (xcross != null) {
            return new SearchResult(prefix.concat(xcross), Goal.XCROSS);
        }
        return null;
    }

    /** Maps stickers through the same logical-frame view used by the CFOP analyzers. */
    private static CubeState normalizeToFrame(CubeState state, cube.CubeOrientation orientation) {
        var view = LogicalFaceletView.of(state, orientation);
        var stickers = new cube.Face[Face.values().length][FaceletState.FACELET_COUNT_PER_FACE];
        for (var face : Face.values()) {
            for (var index = 0; index < FaceletState.FACELET_COUNT_PER_FACE; index++) {
                stickers[face.ordinal()][index] = view.sticker(face, index);
            }
        }
        return CubeConverter.toCubeState(new FaceletState(stickers));
    }

    private static Algorithm search(CubeState start, boolean requireTwoPairs, int maxDepth) {
        if (isGoal(start, requireTwoPairs)) {
            return new Algorithm();
        }
        int lowerBound = heuristic(start, requireTwoPairs);
        for (int depth = lowerBound; depth <= maxDepth; depth++) {
            SolveCancellation.throwIfCancelled();
            var path = new Move[depth];
            var visited = new HashMap<Long, Integer>();
            if (depthFirst(start, requireTwoPairs, path, 0, depth, -1, visited)) {
                var moves = new java.util.ArrayList<Move>(depth);
                for (var move : path) {
                    if (move == null) break;
                    moves.add(move);
                }
                return Algorithm.fromMoves(moves);
            }
        }
        return null;
    }

    private static boolean depthFirst(
            CubeState cube,
            boolean requireTwoPairs,
            Move[] path,
            int pathLength,
            int remainingDepth,
            int lastFace,
            Map<Long, Integer> visited
    ) {
        SolveCancellation.throwIfCancelled();
        if (isGoal(cube, requireTwoPairs)) {
            return true;
        }
        if (remainingDepth == 0) {
            return false;
        }

        long key = stateKey(cube, lastFace);
        var previousRemaining = visited.get(key);
        if (previousRemaining != null && previousRemaining >= remainingDepth) {
            return false;
        }
        visited.put(key, remainingDepth);
        if (heuristic(cube, requireTwoPairs) > remainingDepth) {
            return false;
        }

        for (var move : FACE_TURNS) {
            int face = faceFamily(move);
            if (face == lastFace || commutesOutOfOrder(lastFace, face)) {
                continue;
            }
            MoveApplier.applyMove(cube, move);
            path[pathLength] = move;
            if (depthFirst(cube, requireTwoPairs, path, pathLength + 1,
                    remainingDepth - 1, face, visited)) {
                MoveApplier.applyMove(cube, move.inverse());
                return true;
            }
            MoveApplier.applyMove(cube, move.inverse());
        }
        path[pathLength] = null;
        return false;
    }

    private static int heuristic(CubeState cube, boolean requireTwoPairs) {
        return requireTwoPairs
                ? PruningTables.xxcrossLowerBound(cube)
                : PruningTables.xcrossLowerBound(cube);
    }

    private static boolean isGoal(CubeState cube, boolean requireTwoPairs) {
        for (var edge : D_CROSS) {
            if (cube.edgePerm[edge.ordinal()] != edge.ordinal() || cube.edgeOri[edge.ordinal()] != 0) {
                return false;
            }
        }
        int solvedPairs = 0;
        for (var slot : SLOTS) {
            var corner = slot.corner().ordinal();
            var edge = slot.edge().ordinal();
            if (cube.cornerPerm[corner] == corner && cube.cornerOri[corner] == 0
                    && cube.edgePerm[edge] == edge && cube.edgeOri[edge] == 0) {
                solvedPairs++;
            }
        }
        return solvedPairs >= (requireTwoPairs ? 2 : 1);
    }

    private static long stateKey(CubeState cube, int lastFace) {
        long key = 0;
        int offset = 0;
        for (var edge : TRACKED_EDGES) {
            for (int position = 0; position < cube.edgePerm.length; position++) {
                if (cube.edgePerm[position] == edge.ordinal()) {
                    key |= ((long) (position | (cube.edgeOri[position] << 4))) << offset;
                    offset += 5;
                    break;
                }
            }
        }
        for (var corner : TRACKED_CORNERS) {
            for (int position = 0; position < cube.cornerPerm.length; position++) {
                if (cube.cornerPerm[position] == corner.ordinal()) {
                    key |= ((long) (position | (cube.cornerOri[position] << 3))) << offset;
                    offset += 5;
                    break;
                }
            }
        }
        return key | ((long) (lastFace + 1) << offset);
    }

    private static int faceFamily(Move move) {
        return switch (move) {
            case U, U2, U_PRIME -> 0;
            case R, R2, R_PRIME -> 1;
            case F, F2, F_PRIME -> 2;
            case D, D2, D_PRIME -> 3;
            case L, L2, L_PRIME -> 4;
            case B, B2, B_PRIME -> 5;
            default -> throw new IllegalArgumentException("Unexpected search move: " + move);
        };
    }

    private static boolean commutesOutOfOrder(int previous, int current) {
        if (previous < 0 || axis(previous) != axis(current) || previous == current) {
            return false;
        }
        return rankOnAxis(current) < rankOnAxis(previous);
    }

    private static int axis(int face) {
        return switch (face) {
            case 0, 3 -> 0;
            case 1, 4 -> 1;
            case 2, 5 -> 2;
            default -> throw new IllegalArgumentException("Unknown face family: " + face);
        };
    }

    private static int rankOnAxis(int face) {
        return switch (face) {
            case 0 -> 1; // U after D
            case 3 -> 0;
            case 1 -> 1; // R after L
            case 4 -> 0;
            case 2 -> 1; // F after B
            case 5 -> 0;
            default -> throw new IllegalArgumentException("Unknown face family: " + face);
        };
    }

    enum Goal {
        XXCROSS,
        XCROSS
    }

    record SearchResult(Algorithm algorithm, Goal goal) {
    }
}
