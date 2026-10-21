package solver;

import cube.Algorithm;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.OrientationFrames;
import cube.CubeOrientation;
import cube.OrientedCube;
import solver.pruning.CrossPruningCoordinates;

public class CrossSolver {
    private static final int MAX_CROSS_DEPTH = 12;
    private static final Face[] COLOR_NEUTRAL_FACE_ORDER = {
            Face.U, Face.D, Face.F, Face.B, Face.L, Face.R
    };

    private static final Edge[] D_CROSS_POSITIONS = {Edge.DF, Edge.DR, Edge.DB, Edge.DL};

    public Algorithm solve(CubeState cube) {
        return solveForTargetCross(cube.copy(), new CubeOrientation(), D_CROSS_POSITIONS,
                CrossSearchOptions.defaultsFor(F2LMode.GREEDY));
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        return solve(cube, crossFace, CrossSearchOptions.defaultsFor(F2LMode.GREEDY));
    }

    Algorithm solve(CubeState cube, Face crossFace, CrossSearchOptions options) {
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        // The search runs in the selected cross frame. Keep that frame change
        // in the public algorithm: subsequent moves are interpreted from the
        // rotated user view by cubing.js and by OrientedCube alike.
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveForTargetCross(cube.copy(), orientation, targetCrossForOrientation(orientation), options));
    }

    AdvancedCrossSearch.SearchResult solveOptimizedCross(CubeState cube, Face crossFace) {
        return AdvancedCrossSearch.solve(cube.copy(), crossFace);
    }

    AdvancedCrossSearch.SearchResult solveOptimizedCross(
            CubeState cube,
            Face crossFace,
            CrossSearchOptions options
    ) {
        return AdvancedCrossSearch.solve(cube.copy(), crossFace, options);
    }

    public CrossSolution solveColorNeutral(CubeState cube) {
        CrossSolution best = null;
        for (var face : COLOR_NEUTRAL_FACE_ORDER) {
            SolveCancellation.throwIfCancelled();
            var algorithm = solve(cube, face);
            var candidate = new CrossSolution(face, algorithm);
            if (best == null || algorithm.getMoveCount() < best.algorithm().getMoveCount()) {
                best = candidate;
            }
        }
        return best;
    }

    private Algorithm solveForTargetCross(
            CubeState cube,
            CubeOrientation orientation,
            Edge[] targetCross,
            CrossSearchOptions options
    ) {
        var start = cube.copy();
        if (isTargetCrossSolved(start, targetCross)) {
            return new Algorithm();
        }

        var face = CrossPruningCoordinates.faceFor(targetCross);
        var coordinate = CrossPruningCoordinates.index(start, targetCross);
        var moves = CrossSearchMovePolicy.moves(options);
        Algorithm best = null;
        for (var prefix : CrossSearchMovePolicy.fastYawTrials()) {
            SolveCancellation.throwIfCancelled();
            var mappedFaceTurns = mapFaceTurns(orientationAfterPrefix(orientation, prefix), moves);
            var solution = idaStar(coordinate, face, mappedFaceTurns, moves, options);
            if (solution != null && (best == null || solution.getMoveCount() < best.getMoveCount())) {
                best = prefix.concat(solution);
            }
        }

        if (best != null) return best;
        throw new IllegalStateException("Failed to find a no-B cross solution");
    }

    private static Algorithm idaStar(
            int start,
            Face face,
            Move[] mappedFaceTurns,
            Move[] moves,
            CrossSearchOptions options
    ) {
        var path = new Move[MAX_CROSS_DEPTH];
        var solutionLength = new int[1];
        var initialBound = PruningTables.crossDistance(start, face);
        for (var bound = initialBound; bound <= MAX_CROSS_DEPTH; bound++) {
            SolveCancellation.throwIfCancelled();
            if (search(start, face, mappedFaceTurns, moves, path, 0, bound, -1, solutionLength)) {
                var solution = new Algorithm();
                for (var i = 0; i < solutionLength[0]; i++) solution.add(path[i]);
                return solution;
            }
        }
        return null;
    }

    private static boolean search(
            int coordinate,
            Face face,
            Move[] mappedFaceTurns,
            Move[] moves,
            Move[] path,
            int depth,
            int bound,
            int previousFace,
            int[] solutionLength
    ) {
        SolveCancellation.throwIfCancelled();
        var heuristic = PruningTables.crossDistance(coordinate, face);
        if (depth + heuristic > bound) return false;
        if (heuristic == 0) {
            solutionLength[0] = depth;
            return true;
        }
        if (depth == bound) return false;

        for (var index = 0; index < moves.length; index++) {
            var move = moves[index];
            var moveFace = CrossSearchMovePolicy.faceFamily(move);
            if (CrossSearchMovePolicy.shouldSkip(previousFace, moveFace)) continue;
            path[depth] = move;
            var nextCoordinate = CrossPruningCoordinates.transition(coordinate, mappedFaceTurns[index]);
            if (search(nextCoordinate, face, mappedFaceTurns, moves, path, depth + 1, bound,
                    moveFace, solutionLength)) return true;
        }
        path[depth] = null;
        return false;
    }

    private static boolean isTargetCrossSolved(CubeState cube, Edge[] targetCross) {
        for (var position : targetCross) {
            if (cube.edgePerm[position.ordinal()] != position.ordinal() || cube.edgeOri[position.ordinal()] != 0) {
                return false;
            }
        }
        return true;
    }

    private static Edge[] targetCrossForOrientation(CubeOrientation orientation) {
        return new Edge[]{
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.R)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.L))
        };
    }

    private static CubeOrientation orientationAfterPrefix(CubeOrientation orientation, Algorithm prefix) {
        var orientedCube = new OrientedCube(new CubeState(), orientation);
        orientedCube.applyMoves(prefix.getMoves());
        return orientedCube.orientation();
    }

    private static Move[] mapFaceTurns(CubeOrientation orientation, Move[] moves) {
        var mapped = new Move[moves.length];
        for (int i = 0; i < moves.length; i++) {
            mapped[i] = orientation.mapMove(moves[i]);
        }
        return mapped;
    }

    private static Edge edgeForFaces(Face first, Face second) {
        if (matches(first, second, Face.U, Face.R)) return Edge.UR;
        if (matches(first, second, Face.U, Face.F)) return Edge.UF;
        if (matches(first, second, Face.U, Face.L)) return Edge.UL;
        if (matches(first, second, Face.U, Face.B)) return Edge.UB;
        if (matches(first, second, Face.D, Face.R)) return Edge.DR;
        if (matches(first, second, Face.D, Face.F)) return Edge.DF;
        if (matches(first, second, Face.D, Face.L)) return Edge.DL;
        if (matches(first, second, Face.D, Face.B)) return Edge.DB;
        if (matches(first, second, Face.F, Face.R)) return Edge.FR;
        if (matches(first, second, Face.F, Face.L)) return Edge.FL;
        if (matches(first, second, Face.B, Face.R)) return Edge.BR;
        if (matches(first, second, Face.B, Face.L)) return Edge.BL;

        throw new IllegalArgumentException("Faces do not form an edge: " + first + ", " + second);
    }

    private static boolean matches(Face first, Face second, Face expectedA, Face expectedB) {
        return (first == expectedA && second == expectedB) || (first == expectedB && second == expectedA);
    }

    private static Face faceFamily(Move move) {
        return switch (move) {
            case U, U2, U_PRIME -> Face.U;
            case R, R2, R_PRIME -> Face.R;
            case F, F2, F_PRIME -> Face.F;
            case D, D2, D_PRIME -> Face.D;
            case L, L2, L_PRIME -> Face.L;
            default -> throw new IllegalArgumentException("Unexpected cross move: " + move);
        };
    }
}
