package solver;

import cube.Algorithm;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class CrossSolver {
    private static final Move[] FACE_TURNS = {
            Move.U, Move.U2, Move.U_PRIME,
            Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME,
            Move.D, Move.D2, Move.D_PRIME,
            Move.L, Move.L2, Move.L_PRIME,
            Move.B, Move.B2, Move.B_PRIME
    };

    private static final Edge[] D_CROSS_POSITIONS = {Edge.DF, Edge.DR, Edge.DB, Edge.DL};

    public Algorithm solve(CubeState cube) {
        return solveForTargetCross(copyCube(cube), D_CROSS_POSITIONS, FACE_TURNS);
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        var orientation = orientationToD(crossFace);
        var orientedCube = new cube.OrientedCube(copyCube(cube));
        orientedCube.applyMoves(orientation.getMoves());

        var targetCross = targetCrossForOrientation(orientedCube.orientation());
        var mappedFaceTurns = mapFaceTurns(orientedCube.orientation());
        return orientation.concat(solveForTargetCross(orientedCube.cubeState(), targetCross, mappedFaceTurns));
    }

    private Algorithm solveForTargetCross(CubeState cube, Edge[] targetCross, Move[] mappedFaceTurns) {
        var start = copyCube(cube);
        if (isTargetCrossSolved(start, targetCross)) {
            return new Algorithm();
        }

        var queue = new ArrayDeque<SearchNode>();
        var visited = new HashSet<Integer>();

        queue.add(new SearchNode(start, new Algorithm()));
        visited.add(encodeCrossState(start, targetCross));

        while (!queue.isEmpty()) {
            var current = queue.removeFirst();

            for (int i = 0; i < FACE_TURNS.length; i++) {
                var move = FACE_TURNS[i];
                var mappedMove = mappedFaceTurns[i];
                var nextCube = copyCube(current.cube());
                MoveApplier.applyMove(nextCube, mappedMove);

                var key = encodeCrossState(nextCube, targetCross);
                if (!visited.add(key)) {
                    continue;
                }

                var nextAlgorithm = current.algorithm().copy();
                nextAlgorithm.add(move);

                if (isTargetCrossSolved(nextCube, targetCross)) {
                    return nextAlgorithm;
                }

                queue.addLast(new SearchNode(nextCube, nextAlgorithm));
            }
        }

        throw new IllegalStateException("Failed to find a cross solution");
    }

    private static boolean isTargetCrossSolved(CubeState cube, Edge[] targetCross) {
        for (var position : targetCross) {
            if (cube.edgePerm[position.ordinal()] != position.ordinal() || cube.edgeOri[position.ordinal()] != 0) {
                return false;
            }
        }
        return true;
    }

    private static Algorithm orientationToD(Face face) {
        return switch (face) {
            case D -> new Algorithm();
            case U -> Algorithm.fromMoves(List.of(Move.Z2));
            case R -> Algorithm.fromMoves(List.of(Move.Z));
            case L -> Algorithm.fromMoves(List.of(Move.Z_PRIME));
            case F -> Algorithm.fromMoves(List.of(Move.X));
            case B -> Algorithm.fromMoves(List.of(Move.X_PRIME));
        };
    }

    private static Edge[] targetCrossForOrientation(cube.CubeOrientation orientation) {
        return new Edge[]{
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.R)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.L))
        };
    }

    private static Move[] mapFaceTurns(cube.CubeOrientation orientation) {
        var mapped = new Move[FACE_TURNS.length];
        for (int i = 0; i < FACE_TURNS.length; i++) {
            mapped[i] = orientation.mapMove(FACE_TURNS[i]);
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

    private static CubeState copyCube(CubeState cube) {
        var copy = new CubeState();
        copy.cornerPerm = Arrays.copyOf(cube.cornerPerm, cube.cornerPerm.length);
        copy.cornerOri = Arrays.copyOf(cube.cornerOri, cube.cornerOri.length);
        copy.edgePerm = Arrays.copyOf(cube.edgePerm, cube.edgePerm.length);
        copy.edgeOri = Arrays.copyOf(cube.edgeOri, cube.edgeOri.length);
        return copy;
    }

    private record SearchNode(CubeState cube, Algorithm algorithm) {
    }
}
