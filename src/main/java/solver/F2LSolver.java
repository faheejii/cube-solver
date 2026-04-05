package solver;

import cfop.F2LSlot;
import cube.Algorithm;
import cube.Corner;
import cube.CubeOrientation;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class F2LSolver {
    private static final int MAX_SLOT_SEARCH_DEPTH = 12;

    private static final Move[] FACE_TURNS = {
            Move.U, Move.U2, Move.U_PRIME,
            Move.R, Move.R2, Move.R_PRIME,
            Move.F, Move.F2, Move.F_PRIME,
            Move.L, Move.L2, Move.L_PRIME,
            Move.B, Move.B2, Move.B_PRIME
    };

    private static final Edge[] D_CROSS_EDGES = {Edge.DF, Edge.DR, Edge.DB, Edge.DL};
    private static final F2LSlot[] SLOT_ORDER = {F2LSlot.FR, F2LSlot.FL, F2LSlot.BL, F2LSlot.BR};

    public Algorithm solve(CubeState cube) {
        return solveForTargets(copyCube(cube), D_CROSS_EDGES, canonicalTargets(), FACE_TURNS);
    }

    public Algorithm solveAfterCross(CubeState cube, Face crossFace) {
        var targetCross = targetCrossPiecesAfterCross(crossFace);
        var targetSlots = targetPlacedSlotsAfterCross(crossFace);
        return solveForPlacedTargets(
                copyCube(cube),
                targetCross,
                targetSlots,
                FACE_TURNS
        );
    }

    public Algorithm solve(OrientedCube cube) {
        return solveForTargets(
                copyCube(cube.cubeState()),
                targetCrossForOrientation(cube.orientation()),
                targetSlotsForOrientation(cube.orientation()),
                mapFaceTurns(cube.orientation())
        );
    }

    public Algorithm solve(CubeState cube, Face crossFace) {
        var orientation = orientationToD(crossFace);
        var orientedCube = new OrientedCube(copyCube(cube));
        orientedCube.applyMoves(orientation.getMoves());

        return orientation.concat(
                solveForTargets(
                        orientedCube.cubeState(),
                        targetCrossForOrientation(orientedCube.orientation()),
                        targetSlotsForOrientation(orientedCube.orientation()),
                        mapFaceTurns(orientedCube.orientation())
                )
        );
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot) {
        return solveSlot(cube, slot, Face.D);
    }

    public Algorithm solveSlot(CubeState cube, F2LSlot slot, Face crossFace) {
        var orientation = orientationToD(crossFace);
        var orientedCube = new OrientedCube(copyCube(cube));
        orientedCube.applyMoves(orientation.getMoves());

        return orientation.concat(
                solveSlotInternal(
                        orientedCube.cubeState(),
                        targetCrossForOrientation(orientedCube.orientation()),
                        targetSlotFor(slot, orientedCube.orientation()),
                        List.of(),
                        mapFaceTurns(orientedCube.orientation())
                )
        );
    }

    private Algorithm solveForTargets(CubeState cube, Edge[] targetCross, TargetSlot[] targetSlots, Move[] faceTurns) {
        var workingCube = copyCube(cube);
        ensureCrossSolved(workingCube, targetCross);

        var solution = new Algorithm();
        var protectedSlots = new ArrayList<TargetSlot>();

        for (var targetSlot : targetSlots) {
            if (isTargetSlotSolved(workingCube, targetSlot)) {
                protectedSlots.add(targetSlot);
                continue;
            }

            var slotSolution = solveSlotInternal(workingCube, targetCross, targetSlot, protectedSlots, faceTurns);
            MoveApplier.applyMoves(workingCube, slotSolution.getMoves());
            solution = solution.concat(slotSolution);
            protectedSlots.add(targetSlot);
        }

        return solution;
    }

    private Algorithm solveForPlacedTargets(CubeState cube, Edge[] targetCrossPieces, PlacedTargetSlot[] targetSlots, Move[] faceTurns) {
        var workingCube = copyCube(cube);
        ensurePlacedCrossSolved(workingCube, targetCrossPieces);

        var solution = new Algorithm();
        var protectedSlots = new ArrayList<PlacedTargetSlot>();

        for (var targetSlot : targetSlots) {
            if (isPlacedTargetSlotSolved(workingCube, targetSlot)) {
                protectedSlots.add(targetSlot);
                continue;
            }

            var slotSolution = solvePlacedSlotInternal(workingCube, targetCrossPieces, targetSlot, protectedSlots, faceTurns);
            MoveApplier.applyMoves(workingCube, slotSolution.getMoves());
            solution = solution.concat(slotSolution);
            protectedSlots.add(targetSlot);
        }

        return solution;
    }

    private Algorithm solveSlotInternal(
            CubeState cube,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            Move[] faceTurns
    ) {
        ensureCrossSolved(cube, targetCross);
        if (areGoalsSolved(cube, targetCross, targetSlot, protectedSlots)) {
            return new Algorithm();
        }

        var path = new ArrayList<Move>();
        for (int depthLimit = 1; depthLimit <= MAX_SLOT_SEARCH_DEPTH; depthLimit++) {
            var solution = depthLimitedSearch(
                    copyCube(cube),
                    targetCross,
                    targetSlot,
                    protectedSlots,
                    faceTurns,
                    path,
                    null,
                    depthLimit
            );
            if (solution != null) {
                return solution;
            }
        }

        throw new IllegalStateException("Failed to find an F2L solution for slot " + targetSlot);
    }

    private Algorithm solvePlacedSlotInternal(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots,
            Move[] faceTurns
    ) {
        ensurePlacedCrossSolved(cube, targetCrossPieces);
        if (arePlacedGoalsSolved(cube, targetCrossPieces, targetSlot, protectedSlots)) {
            return new Algorithm();
        }

        var path = new ArrayList<Move>();
        for (int depthLimit = 1; depthLimit <= MAX_SLOT_SEARCH_DEPTH; depthLimit++) {
            var solution = depthLimitedPlacedSearch(
                    copyCube(cube),
                    targetCrossPieces,
                    targetSlot,
                    protectedSlots,
                    faceTurns,
                    path,
                    null,
                    depthLimit
            );
            if (solution != null) {
                return solution;
            }
        }

        throw new IllegalStateException("Failed to find an F2L solution for slot " + targetSlot);
    }

    private Algorithm depthLimitedSearch(
            CubeState cube,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots,
            Move[] faceTurns,
            List<Move> path,
            Move lastMove,
            int remainingDepth
    ) {
        if (areGoalsSolved(cube, targetCross, targetSlot, protectedSlots)) {
            return Algorithm.fromMoves(List.copyOf(path));
        }
        if (remainingDepth == 0) {
            return null;
        }

        for (int i = 0; i < FACE_TURNS.length; i++) {
            var move = FACE_TURNS[i];
            if (lastMove != null && sameFace(lastMove, move)) {
                continue;
            }

            var nextCube = copyCube(cube);
            MoveApplier.applyMove(nextCube, faceTurns[i]);
            path.add(move);

            var solution = depthLimitedSearch(
                    nextCube,
                    targetCross,
                    targetSlot,
                    protectedSlots,
                    faceTurns,
                    path,
                    move,
                    remainingDepth - 1
            );
            if (solution != null) {
                return solution;
            }

            path.remove(path.size() - 1);
        }

        return null;
    }

    private Algorithm depthLimitedPlacedSearch(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots,
            Move[] faceTurns,
            List<Move> path,
            Move lastMove,
            int remainingDepth
    ) {
        if (arePlacedGoalsSolved(cube, targetCrossPieces, targetSlot, protectedSlots)) {
            return Algorithm.fromMoves(List.copyOf(path));
        }
        if (remainingDepth == 0) {
            return null;
        }

        for (int i = 0; i < FACE_TURNS.length; i++) {
            var move = FACE_TURNS[i];
            if (lastMove != null && sameFace(lastMove, move)) {
                continue;
            }

            var nextCube = copyCube(cube);
            MoveApplier.applyMove(nextCube, faceTurns[i]);
            path.add(move);

            var solution = depthLimitedPlacedSearch(
                    nextCube,
                    targetCrossPieces,
                    targetSlot,
                    protectedSlots,
                    faceTurns,
                    path,
                    move,
                    remainingDepth - 1
            );
            if (solution != null) {
                return solution;
            }

            path.remove(path.size() - 1);
        }

        return null;
    }

    private static boolean areGoalsSolved(
            CubeState cube,
            Edge[] targetCross,
            TargetSlot targetSlot,
            List<TargetSlot> protectedSlots
    ) {
        if (!isTargetCrossSolved(cube, targetCross)) {
            return false;
        }
        if (!isTargetSlotSolved(cube, targetSlot)) {
            return false;
        }
        for (var slot : protectedSlots) {
            if (!isTargetSlotSolved(cube, slot)) {
                return false;
            }
        }
        return true;
    }

    private static boolean arePlacedGoalsSolved(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots
    ) {
        if (!isPlacedCrossSolved(cube, targetCrossPieces)) {
            return false;
        }
        if (!isPlacedTargetSlotSolved(cube, targetSlot)) {
            return false;
        }
        for (var slot : protectedSlots) {
            if (!isPlacedTargetSlotSolved(cube, slot)) {
                return false;
            }
        }
        return true;
    }

    private static long encodeState(CubeState cube, Edge[] targetCross, TargetSlot targetSlot, List<TargetSlot> protectedSlots) {
        long key = 0L;
        int shift = 0;

        for (var edge : targetCross) {
            key |= ((long) encodeEdge(cube, edge)) << shift;
            shift += 5;
        }

        for (var slot : relevantSlots(targetSlot, protectedSlots)) {
            key |= ((long) encodeCorner(cube, slot.corner())) << shift;
            shift += 5;
            key |= ((long) encodeEdge(cube, slot.edge())) << shift;
            shift += 5;
        }

        return key;
    }

    private static long encodePlacedState(
            CubeState cube,
            Edge[] targetCrossPieces,
            PlacedTargetSlot targetSlot,
            List<PlacedTargetSlot> protectedSlots
    ) {
        long key = 0L;
        int shift = 0;

        for (var edge : targetCrossPieces) {
            key |= ((long) encodeEdge(cube, edge)) << shift;
            shift += 5;
        }

        for (var slot : relevantPlacedSlots(targetSlot, protectedSlots)) {
            key |= ((long) encodeCorner(cube, slot.cornerPiece())) << shift;
            shift += 5;
            key |= ((long) encodeEdge(cube, slot.edgePiece())) << shift;
            shift += 5;
        }

        return key;
    }

    private static List<TargetSlot> relevantSlots(TargetSlot targetSlot, List<TargetSlot> protectedSlots) {
        var slots = new ArrayList<TargetSlot>(protectedSlots.size() + 1);
        slots.add(targetSlot);
        for (var slot : protectedSlots) {
            if (slot != targetSlot) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static List<PlacedTargetSlot> relevantPlacedSlots(PlacedTargetSlot targetSlot, List<PlacedTargetSlot> protectedSlots) {
        var slots = new ArrayList<PlacedTargetSlot>(protectedSlots.size() + 1);
        slots.add(targetSlot);
        for (var slot : protectedSlots) {
            if (slot != targetSlot) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static int encodeCorner(CubeState cube, Corner targetCorner) {
        for (var position : Corner.values()) {
            if (cube.cornerPerm[position.ordinal()] == targetCorner.ordinal()) {
                return position.ordinal() | (cube.cornerOri[position.ordinal()] << 3);
            }
        }
        throw new IllegalStateException("Missing F2L corner: " + targetCorner);
    }

    private static int encodeEdge(CubeState cube, Edge targetEdge) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == targetEdge.ordinal()) {
                return position.ordinal() | (cube.edgeOri[position.ordinal()] << 4);
            }
        }
        throw new IllegalStateException("Missing F2L edge: " + targetEdge);
    }

    private static boolean sameFace(Move first, Move second) {
        return faceFamily(first) == faceFamily(second);
    }

    private static Face faceFamily(Move move) {
        return switch (move) {
            case U, U2, U_PRIME -> Face.U;
            case R, R2, R_PRIME -> Face.R;
            case F, F2, F_PRIME -> Face.F;
            case L, L2, L_PRIME -> Face.L;
            case B, B2, B_PRIME -> Face.B;
            default -> throw new IllegalArgumentException("Unexpected F2L move: " + move);
        };
    }

    private static void ensureCrossSolved(CubeState cube, Edge[] targetCross) {
        if (!isTargetCrossSolved(cube, targetCross)) {
            throw new IllegalArgumentException("F2L requires a solved D cross");
        }
    }

    private static void ensurePlacedCrossSolved(CubeState cube, Edge[] targetCrossPieces) {
        if (!isPlacedCrossSolved(cube, targetCrossPieces)) {
            throw new IllegalArgumentException("F2L requires a solved D cross");
        }
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

    private static CubeOrientation orientedFrameFor(Face face) {
        var cube = new OrientedCube();
        cube.applyMoves(orientationToD(face).getMoves());
        return cube.orientation();
    }

    private static Edge[] targetCrossPiecesAfterCross(Face face) {
        var solved = new CubeState();
        MoveApplier.applyMoves(solved, orientationToD(face).getMoves());
        return new Edge[]{
                Edge.values()[solved.edgePerm[Edge.DF.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DR.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DB.ordinal()]],
                Edge.values()[solved.edgePerm[Edge.DL.ordinal()]]
        };
    }

    private static PlacedTargetSlot[] targetPlacedSlotsAfterCross(Face face) {
        var solved = new CubeState();
        MoveApplier.applyMoves(solved, orientationToD(face).getMoves());
        return new PlacedTargetSlot[]{
                new PlacedTargetSlot(
                        Corner.DFR,
                        Corner.values()[solved.cornerPerm[Corner.DFR.ordinal()]],
                        Edge.FR,
                        Edge.values()[solved.edgePerm[Edge.FR.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DLF,
                        Corner.values()[solved.cornerPerm[Corner.DLF.ordinal()]],
                        Edge.FL,
                        Edge.values()[solved.edgePerm[Edge.FL.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DBL,
                        Corner.values()[solved.cornerPerm[Corner.DBL.ordinal()]],
                        Edge.BL,
                        Edge.values()[solved.edgePerm[Edge.BL.ordinal()]]
                ),
                new PlacedTargetSlot(
                        Corner.DRB,
                        Corner.values()[solved.cornerPerm[Corner.DRB.ordinal()]],
                        Edge.BR,
                        Edge.values()[solved.edgePerm[Edge.BR.ordinal()]]
                )
        };
    }

    private static TargetSlot[] canonicalTargets() {
        return new TargetSlot[]{
                new TargetSlot(F2LSlot.FR.corner(), F2LSlot.FR.edge()),
                new TargetSlot(F2LSlot.FL.corner(), F2LSlot.FL.edge()),
                new TargetSlot(F2LSlot.BL.corner(), F2LSlot.BL.edge()),
                new TargetSlot(F2LSlot.BR.corner(), F2LSlot.BR.edge())
        };
    }

    private static TargetSlot[] targetSlotsForOrientation(CubeOrientation orientation) {
        return new TargetSlot[]{
                targetSlotFor(F2LSlot.FR, orientation),
                targetSlotFor(F2LSlot.FL, orientation),
                targetSlotFor(F2LSlot.BL, orientation),
                targetSlotFor(F2LSlot.BR, orientation)
        };
    }

    private static TargetSlot targetSlotFor(F2LSlot slot, CubeOrientation orientation) {
        return switch (slot) {
            case FR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.R))
            );
            case FL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.F), orientation.faceAt(Face.L))
            );
            case BL -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.L)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.L))
            );
            case BR -> new TargetSlot(
                    cornerForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B), orientation.faceAt(Face.R)),
                    edgeForFaces(orientation.faceAt(Face.B), orientation.faceAt(Face.R))
            );
        };
    }

    private static Edge[] targetCrossForOrientation(CubeOrientation orientation) {
        return new Edge[]{
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.F)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.R)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.B)),
                edgeForFaces(orientation.faceAt(Face.D), orientation.faceAt(Face.L))
        };
    }

    private static Move[] mapFaceTurns(CubeOrientation orientation) {
        var mapped = new Move[FACE_TURNS.length];
        for (int i = 0; i < FACE_TURNS.length; i++) {
            mapped[i] = orientation.mapMove(FACE_TURNS[i]);
        }
        return mapped;
    }

    private static boolean isTargetCrossSolved(CubeState cube, Edge[] targetCross) {
        for (var edge : targetCross) {
            if (cube.edgePerm[edge.ordinal()] != edge.ordinal() || cube.edgeOri[edge.ordinal()] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPlacedCrossSolved(CubeState cube, Edge[] targetCrossPieces) {
        for (int i = 0; i < D_CROSS_EDGES.length; i++) {
            var position = D_CROSS_EDGES[i];
            var piece = targetCrossPieces[i];
            if (cube.edgePerm[position.ordinal()] != piece.ordinal() || cube.edgeOri[position.ordinal()] != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTargetSlotSolved(CubeState cube, TargetSlot slot) {
        return cube.cornerPerm[slot.corner().ordinal()] == slot.corner().ordinal()
                && cube.cornerOri[slot.corner().ordinal()] == 0
                && cube.edgePerm[slot.edge().ordinal()] == slot.edge().ordinal()
                && cube.edgeOri[slot.edge().ordinal()] == 0;
    }

    private static boolean isPlacedTargetSlotSolved(CubeState cube, PlacedTargetSlot slot) {
        return cube.cornerPerm[slot.cornerPosition().ordinal()] == slot.cornerPiece().ordinal()
                && cube.cornerOri[slot.cornerPosition().ordinal()] == 0
                && cube.edgePerm[slot.edgePosition().ordinal()] == slot.edgePiece().ordinal()
                && cube.edgeOri[slot.edgePosition().ordinal()] == 0;
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

    private static Corner cornerForFaces(Face a, Face b, Face c) {
        if (matchesAll(a, b, c, Face.U, Face.R, Face.F)) return Corner.URF;
        if (matchesAll(a, b, c, Face.U, Face.F, Face.L)) return Corner.UFL;
        if (matchesAll(a, b, c, Face.U, Face.L, Face.B)) return Corner.ULB;
        if (matchesAll(a, b, c, Face.U, Face.B, Face.R)) return Corner.UBR;
        if (matchesAll(a, b, c, Face.D, Face.F, Face.R)) return Corner.DFR;
        if (matchesAll(a, b, c, Face.D, Face.L, Face.F)) return Corner.DLF;
        if (matchesAll(a, b, c, Face.D, Face.B, Face.L)) return Corner.DBL;
        if (matchesAll(a, b, c, Face.D, Face.R, Face.B)) return Corner.DRB;
        throw new IllegalArgumentException("Faces do not form a corner: " + a + ", " + b + ", " + c);
    }

    private static boolean matches(Face first, Face second, Face expectedA, Face expectedB) {
        return (first == expectedA && second == expectedB) || (first == expectedB && second == expectedA);
    }

    private static boolean matchesAll(Face a, Face b, Face c, Face x, Face y, Face z) {
        return contains(a, b, c, x) && contains(a, b, c, y) && contains(a, b, c, z);
    }

    private static boolean contains(Face a, Face b, Face c, Face target) {
        return a == target || b == target || c == target;
    }

    private static CubeState copyCube(CubeState cube) {
        var copy = new CubeState();
        copy.cornerPerm = Arrays.copyOf(cube.cornerPerm, cube.cornerPerm.length);
        copy.cornerOri = Arrays.copyOf(cube.cornerOri, cube.cornerOri.length);
        copy.edgePerm = Arrays.copyOf(cube.edgePerm, cube.edgePerm.length);
        copy.edgeOri = Arrays.copyOf(cube.edgeOri, cube.edgeOri.length);
        return copy;
    }
    private record TargetSlot(Corner corner, Edge edge) {
    }

    private record PlacedTargetSlot(Corner cornerPosition, Corner cornerPiece, Edge edgePosition, Edge edgePiece) {
    }
}
