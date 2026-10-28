package solver;

import java.util.HashMap;
import java.util.Map;

import cfop.F2LSlot;
import cube.Algorithm;
import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientationFrames;

/** Searches for a cross with two or one F2L pairs before normal cross fallback. */
final class AdvancedCrossSearch {
    private static final int XXCROSS_MAX_DEPTH = 10;
    private static final int CROSS_MAX_DEPTH = 8;
    private static final F2LSlot[] SLOTS = F2LSlot.values();
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
        return solve(input, crossFace, SearchPolicy.UNRESTRICTED);
    }

    static SearchResult solve(CubeState input, Face crossFace, CrossSearchOptions options) {
        if (options == null) throw new IllegalArgumentException("Cross search options are required");
        var policy = SearchPolicy.forOptions(options);
        return solve(input, crossFace, policy, XXCROSS_MAX_DEPTH, CROSS_MAX_DEPTH);
    }

    static SearchResult solve(CubeState input, Face crossFace, SearchPolicy policy) {
        return solve(input, crossFace, policy, XXCROSS_MAX_DEPTH, CROSS_MAX_DEPTH);
    }

    static SearchResult solve(
            CubeState input,
            Face crossFace,
            SearchPolicy policy,
            int xxcrossMaxDepth,
            int xcrossMaxDepth
    ) {
        if (input == null || crossFace == null || policy == null) {
            throw new IllegalArgumentException("Cube state, cross face, and search policy are required");
        }
        if (xxcrossMaxDepth < 0 || xcrossMaxDepth < 0) {
            throw new IllegalArgumentException("Search depth limits cannot be negative");
        }
        var prefix = OrientationFrames.orientationToD(crossFace);
        var initialOrientation = OrientationFrames.orientedFrameFor(crossFace);
        var trials = policy.yawTrials();
        var moves = policy.faceTurns();

        var xxcross = searchAcrossTrials(input, initialOrientation, trials, moves, true,
                xxcrossMaxDepth);
        if (xxcross != null) {
            return new SearchResult(prefix.concat(xxcross.algorithm()), Goal.XXCROSS);
        }

        var xcross = searchAcrossTrials(input, initialOrientation, trials, moves, false,
                xcrossMaxDepth);
        if (xcross != null) {
            return new SearchResult(prefix.concat(xcross.algorithm()), Goal.XCROSS);
        }
        return null;
    }

    private static TrialResult searchAcrossTrials(
            CubeState input,
            cube.CubeOrientation initialOrientation,
            YawTrial[] trials,
            Move[] moves,
            boolean requireTwoPairs,
            int maxDepth
    ) {
        TrialResult best = null;
        for (var trial : trials) {
            SolveCancellation.throwIfCancelled();
            var orientation = initialOrientation.copy();
            var orientedCube = new cube.OrientedCube(new CubeState(), orientation);
            orientedCube.applyMoves(trial.rotation().getMoves());
            var normalized = normalizeToFrame(input, orientedCube.orientation());
            var solution = search(normalized, requireTwoPairs, maxDepth, moves);
            if (solution == null) continue;

            var candidate = new TrialResult(trial.rotation().concat(solution), solution.getMoveCount());
            if (best == null || candidate.faceTurnCount() < best.faceTurnCount()) best = candidate;
        }
        return best;
    }

    /** Maps stickers through the same logical-frame view used by the CFOP analyzers. */
    private static CubeState normalizeToFrame(CubeState state, cube.CubeOrientation orientation) {
        return LogicalFrameNormalizer.normalize(state, orientation);
    }

    private static Algorithm search(
            CubeState start,
            boolean requireTwoPairs,
            int maxDepth,
            Move[] faceTurns
    ) {
        if (isGoal(start, requireTwoPairs)) {
            return new Algorithm();
        }
        int lowerBound = heuristic(start, requireTwoPairs);
        for (int depth = lowerBound; depth <= maxDepth; depth++) {
            SolveCancellation.throwIfCancelled();
            var path = new Move[depth];
            var visited = new HashMap<Long, Integer>();
            if (depthFirst(start, requireTwoPairs, path, 0, depth, -1, visited, faceTurns)) {
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
            Map<Long, Integer> visited,
            Move[] faceTurns
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

        for (var move : faceTurns) {
            int face = CrossSearchMovePolicy.faceFamily(move);
            if (CrossSearchMovePolicy.shouldSkip(lastFace, face)) continue;
            MoveApplier.applyMove(cube, move);
            path[pathLength] = move;
            if (depthFirst(cube, requireTwoPairs, path, pathLength + 1,
                    remainingDepth - 1, face, visited, faceTurns)) {
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
        key |= (long) (lastFace + 1) << offset;
        return key;
    }

    enum Goal {
        XXCROSS,
        XCROSS
    }

    record SearchResult(Algorithm algorithm, Goal goal) {
    }

    enum SearchPolicy {
        UNRESTRICTED(new CrossSearchOptions(false)),
        NO_B_FOUR_Y(new CrossSearchOptions(true));

        private final Move[] faceTurns;
        private final YawTrial[] yawTrials;

        SearchPolicy(CrossSearchOptions options) {
            this.faceTurns = CrossSearchMovePolicy.moves(options);
            this.yawTrials = CrossSearchMovePolicy.advancedYawTrials(options).stream()
                    .map(YawTrial::new).toArray(YawTrial[]::new);
        }

        static SearchPolicy forOptions(CrossSearchOptions options) {
            for (var policy : values()) {
                if ((policy == NO_B_FOUR_Y) == options.avoidBTurns()) return policy;
            }
            throw new IllegalStateException("No advanced cross policy for " + options);
        }

        Move[] faceTurns() {
            return faceTurns;
        }

        YawTrial[] yawTrials() {
            return yawTrials;
        }
    }

    private record YawTrial(Algorithm rotation) {
    }

    private record TrialResult(Algorithm algorithm, int faceTurnCount) {
    }
}
