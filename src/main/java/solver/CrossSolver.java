package solver;

import cube.Algorithm;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientationFrames;
import cube.CubeOrientation;
import cube.OrientedCube;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrossSolver {
    private static final int MAX_CROSS_DEPTH = 12;
    private static final Move[] NO_B_FACE_TURNS = {
            Move.U, Move.U2, Move.U_PRIME,
            Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME,
            Move.D, Move.D2, Move.D_PRIME,
            Move.L, Move.L2, Move.L_PRIME
    };
    private static final Algorithm[] Y_PREFIX_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.Y)),
            Algorithm.fromMoves(List.of(Move.Y2)),
            Algorithm.fromMoves(List.of(Move.Y_PRIME))
    };
    private static final Face[] COLOR_NEUTRAL_FACE_ORDER = {
            Face.U, Face.D, Face.F, Face.B, Face.L, Face.R
    };

    private static final Edge[] D_CROSS_POSITIONS = {Edge.DF, Edge.DR, Edge.DB, Edge.DL};

    public Algorithm solve(CubeState cube) {
        return solveForTargetCross(cube.copy(), new CubeOrientation(), D_CROSS_POSITIONS);
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        var orientation = OrientationFrames.orientedFrameFor(crossFace);
        return OrientationFrames.orientationToD(crossFace)
                .concat(solveForTargetCross(cube.copy(), orientation, targetCrossForOrientation(orientation)));
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

    private Algorithm solveForTargetCross(CubeState cube, CubeOrientation orientation, Edge[] targetCross) {
        var start = cube.copy();
        if (isTargetCrossSolved(start, targetCross)) {
            return new Algorithm();
        }

        for (int depth = 0; depth <= MAX_CROSS_DEPTH; depth++) {
            SolveCancellation.throwIfCancelled();
            for (var prefix : Y_PREFIX_TRIALS) {
                var prefixedOrientation = orientationAfterPrefix(orientation, prefix);
                var mappedFaceTurns = mapFaceTurns(prefixedOrientation);
                var solution = depthLimitedSearch(
                        start,
                        targetCross,
                        mappedFaceTurns,
                        new Algorithm(),
                        null,
                        depth,
                        new HashMap<>()
                );
                if (solution != null) {
                    return prefix.concat(solution);
                }
            }
        }

        throw new IllegalStateException("Failed to find a no-B cross solution");
    }

    private static Algorithm depthLimitedSearch(
            CubeState cube,
            Edge[] targetCross,
            Move[] mappedFaceTurns,
            Algorithm path,
            Move lastMove,
            int remainingDepth,
            Map<Integer, Integer> visited
    ) {
        SolveCancellation.throwIfCancelled();
        if (isTargetCrossSolved(cube, targetCross)) {
            return path;
        }
        if (remainingDepth == 0) {
            return null;
        }

        var key = encodeCrossState(cube, targetCross);
        var bestRemainingDepth = visited.get(key);
        if (bestRemainingDepth != null && bestRemainingDepth >= remainingDepth) {
            return null;
        }
        visited.put(key, remainingDepth);

        for (int i = 0; i < NO_B_FACE_TURNS.length; i++) {
            var move = NO_B_FACE_TURNS[i];
            if (lastMove != null && sameFace(lastMove, move)) {
                continue;
            }

            var nextCube = cube.copy();
            MoveApplier.applyMove(nextCube, mappedFaceTurns[i]);

            var nextPath = path.copy();
            nextPath.add(move);

            var solution = depthLimitedSearch(
                    nextCube,
                    targetCross,
                    mappedFaceTurns,
                    nextPath,
                    move,
                    remainingDepth - 1,
                    visited
            );
            if (solution != null) {
                return solution;
            }
        }

        return null;
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

    private static Move[] mapFaceTurns(CubeOrientation orientation) {
        var mapped = new Move[NO_B_FACE_TURNS.length];
        for (int i = 0; i < NO_B_FACE_TURNS.length; i++) {
            mapped[i] = orientation.mapMove(NO_B_FACE_TURNS[i]);
        }
        return mapped;
    }

    private static int encodeCrossState(CubeState cube, Edge[] targetCross) {
        return encodeEdge(cube, targetCross[0])
                | (encodeEdge(cube, targetCross[1]) << 5)
                | (encodeEdge(cube, targetCross[2]) << 10)
                | (encodeEdge(cube, targetCross[3]) << 15);
    }

    private static int encodeEdge(CubeState cube, Edge targetEdge) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == targetEdge.ordinal()) {
                var orientation = cube.edgeOri[position.ordinal()];
                return position.ordinal() | (orientation << 4);
            }
        }
        throw new IllegalStateException("Missing cross edge: " + targetEdge);
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

    private static boolean sameFace(Move first, Move second) {
        return faceFamily(first) == faceFamily(second);
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
