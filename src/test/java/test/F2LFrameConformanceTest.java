package test;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import algorithms.F2LInsertCaseDatabase;
import algorithms.F2LSetupCaseDatabase;
import cube.Corner;
import cube.Algorithm;
import cube.CubeOrientation;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.MoveApplier;
import cube.Move;
import cube.OrientationFrames;
import cube.OrientedCube;
import io.CubeConverter;
import io.FaceletState;
import org.junit.jupiter.api.Test;
import solver.CrossSolver;
import solver.F2LSolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance coverage for the frame-aware F2L boundary.
 *
 * <p>The reference signature deliberately does not call the production
 * {@link F2LCaseSignatureExtractor}: it reads physical stickers, maps both
 * positions and colors into the requested frame, then derives orientations
 * from the face on which each target cubie's U/D (or first edge) sticker sits.
 * This catches a raw cubie-orientation value being used after a whole-cube
 * frame rotation.</p>
 */
class F2LFrameConformanceTest {
    private static final String SUPPLIED_SCRAMBLE =
            "U R2 B2 R' B R D F B' U2 D2 R2 B U2 R2 U2 L2 F' U2 L";

    private static final StickerRef[][] CORNER_STICKERS = {
            {sticker(Face.U, 8), sticker(Face.R, 0), sticker(Face.F, 2)},
            {sticker(Face.U, 6), sticker(Face.F, 0), sticker(Face.L, 2)},
            {sticker(Face.U, 0), sticker(Face.L, 0), sticker(Face.B, 2)},
            {sticker(Face.U, 2), sticker(Face.B, 0), sticker(Face.R, 2)},
            {sticker(Face.D, 2), sticker(Face.F, 8), sticker(Face.R, 6)},
            {sticker(Face.D, 0), sticker(Face.L, 8), sticker(Face.F, 6)},
            {sticker(Face.D, 6), sticker(Face.B, 8), sticker(Face.L, 6)},
            {sticker(Face.D, 8), sticker(Face.R, 8), sticker(Face.B, 6)}
    };

    private static final StickerRef[][] EDGE_STICKERS = {
            {sticker(Face.U, 5), sticker(Face.R, 1)},
            {sticker(Face.U, 7), sticker(Face.F, 1)},
            {sticker(Face.U, 3), sticker(Face.L, 1)},
            {sticker(Face.U, 1), sticker(Face.B, 1)},
            {sticker(Face.D, 5), sticker(Face.R, 7)},
            {sticker(Face.D, 1), sticker(Face.F, 7)},
            {sticker(Face.D, 3), sticker(Face.L, 7)},
            {sticker(Face.D, 7), sticker(Face.B, 7)},
            {sticker(Face.F, 5), sticker(Face.R, 3)},
            {sticker(Face.F, 3), sticker(Face.L, 5)},
            {sticker(Face.B, 5), sticker(Face.L, 3)},
            {sticker(Face.B, 3), sticker(Face.R, 5)}
    };

    @Test
    void extract_shouldMatchFaceletReferenceForEverySlotInAll24Frames() {
        var algorithms = List.of(
                "",
                "R U R' U' F2 D L2 B'",
                "B R' F U D' R D' R2 B U2 R U2 L' D2 R F2 R2 D2 R B2 R2",
                "x R2 y U' z F L2 D' B2 R U2",
                SUPPLIED_SCRAMBLE
        );

        assertEquals(24, CubeOrientationKey.all().size());
        for (var algorithm : algorithms) {
            var cube = new CubeState();
            MoveApplier.applyAlgorithm(cube, algorithm);
            for (var frameKey : CubeOrientationKey.all()) {
                var frame = frameKey.toOrientation();
                for (var slot : F2LSlot.values()) {
                    assertEquals(
                            referenceSignature(cube, slot, frame),
                            F2LCaseSignatureExtractor.extract(cube, slot, frame),
                            () -> "algorithm=" + algorithm + " frame=" + frameKey + " slot=" + slot
                    );
                }
            }
        }
    }

    @Test
    void crossFaceFrames_shouldPlaceTheRequestedCrossOnLogicalDown() {
        assertEquals("", OrientationFrames.orientationToD(Face.D).toString());
        assertEquals("z2", OrientationFrames.orientationToD(Face.U).toString());
        assertEquals("z", OrientationFrames.orientationToD(Face.R).toString());
        assertEquals("z'", OrientationFrames.orientationToD(Face.L).toString());
        assertEquals("x'", OrientationFrames.orientationToD(Face.F).toString());
        assertEquals("x", OrientationFrames.orientationToD(Face.B).toString());

        for (var crossFace : Face.values()) {
            var frame = OrientationFrames.orientedFrameFor(crossFace);
            assertEquals(crossFace, frame.faceAt(Face.D), crossFace + " must be logical D");
        }
    }

    @Test
    void suppliedScramble_shouldCompleteF2lForLeftCross() {
        assertSuppliedScrambleCompletesF2l(Face.L);
    }

    @Test
    void suppliedScramble_shouldCompleteF2lForRightCross() {
        assertSuppliedScrambleCompletesF2l(Face.R);
    }

    @Test
    void suppliedScramble_shouldExposeUPrimeCase17ThenCase23ForLeftCross() {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, SUPPLIED_SCRAMBLE);
        var cross = new CrossSolver().solve(scrambled, Face.L);
        assertEquals("z' y R' D' L2 D2 L' F'", cross.toString());

        // The internal model starts from the scrambled physical state and
        // tracks the visible z' y frame exactly once through the public Cross.
        var oriented = new OrientedCube(scrambled.copy());
        oriented.applyMoves(cross.getMoves());
        oriented.applyMoves(List.of(Move.U_PRIME));

        var setupDb = F2LSetupCaseDatabase.seedCases();
        var insertDb = F2LInsertCaseDatabase.seedCases();
        var flSignature = F2LCaseSignatureExtractor.extract(
                oriented.cubeState(), F2LSlot.FL, oriented.orientation()
        );
        var setup = setupDb.findCompatible(
                        cfop.F2LPreservationMask.empty(), cfop.F2LSetupSignature.from(flSignature)
                ).stream()
                .filter(candidate -> candidate.name().equals("case-17.2"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("case-17.2 did not match FL after U': " + flSignature
                        + ", catalog=" + setupDb.allCases().stream()
                        .filter(candidate -> candidate.name().equals("case-17.2"))
                        .findFirst().orElseThrow().signature()));

        oriented.applyMoves(setup.algorithm().getMoves());
        var insertSignature = F2LCaseSignatureExtractor.extract(
                oriented.cubeState(), F2LSlot.FL, oriented.orientation()
        );
        assertTrue(insertDb.findCompatible(F2LSlot.FL, cfop.F2LPreservationMask.empty(), insertSignature).stream()
                .anyMatch(candidate -> candidate.name().equals("case-2.3")),
                () -> "case-2.3 did not match FL after U' + case-17.2: " + insertSignature);

        var route = Algorithm.parse(cross + " U' L U L' F U' F'");
        var rawRoute = new CubeState();
        MoveApplier.applyAlgorithm(rawRoute, SUPPLIED_SCRAMBLE);
        MoveApplier.applyMoves(rawRoute, route.getMoves());
        var finalInternal = new OrientedCube(scrambled.copy());
        finalInternal.applyMoves(route.getMoves());
        canonicalizeVisibleFrame(rawRoute, finalInternal.orientation());
        assertArrayEquals(finalInternal.cubeState().cornerPerm, rawRoute.cornerPerm, "L route corner permutation");
        assertArrayEquals(finalInternal.cubeState().cornerOri, rawRoute.cornerOri, "L route corner orientation");
        assertArrayEquals(finalInternal.cubeState().edgePerm, rawRoute.edgePerm, "L route edge permutation");
        assertArrayEquals(finalInternal.cubeState().edgeOri, rawRoute.edgeOri, "L route edge orientation");
    }

    @Test
    void case17Point2_shouldBeDiscoverableFromEveryWholeCubeFrame() {
        var database = F2LSetupCaseDatabase.seedCases();
        var setupCase = database.allCases().stream()
                .filter(candidate -> candidate.name().equals("case-17.2"))
                .findFirst()
                .orElseThrow();

        for (var frameKey : CubeOrientationKey.all()) {
            var source = new OrientedCube(new CubeState(), frameKey.toOrientation());
            source.applyMoves(setupCase.sourceSetup().getMoves());
            var signature = F2LCaseSignatureExtractor.extract(
                    source.cubeState(), setupCase.nonPreservedSlot(), source.orientation()
            );
            assertTrue(database.findCompatible(
                            F2LPreservationMask.empty(), cfop.F2LSetupSignature.from(signature)
                    ).stream()
                            .anyMatch(candidate -> candidate.name().equals("case-17.2")),
                    () -> "case-17.2 missing for starting frame=" + frameKey
                            + " source frame=" + source.orientation()
                            + " signature=" + signature);
        }
    }

    @Test
    void case2Point3_shouldBeDiscoverableAndSolveFlFromEveryWholeCubeFrame() {
        var database = F2LInsertCaseDatabase.seedCases();
        var insertCase = database.allCases().stream()
                .filter(candidate -> candidate.name().equals("case-2.3"))
                .findFirst()
                .orElseThrow();

        for (var frameKey : CubeOrientationKey.all()) {
            var source = new OrientedCube(new CubeState(), frameKey.toOrientation());
            source.applyMoves(insertCase.algorithm().inverse().getMoves());
            var signature = F2LCaseSignatureExtractor.extract(
                    source.cubeState(), insertCase.insertSlot(), source.orientation()
            );
            assertTrue(database.findCompatible(
                            insertCase.insertSlot(), F2LPreservationMask.empty(), signature
                    ).stream().anyMatch(candidate -> candidate.name().equals("case-2.3")),
                    () -> "case-2.3 missing for frame=" + frameKey + " signature=" + signature);

            source.applyMoves(insertCase.algorithm().getMoves());
            assertTrue(CrossAnalyzer.isCrossSolved(source.cubeState(), source.orientation()),
                    () -> "case-2.3 broke cross in frame=" + frameKey);
            assertTrue(F2LAnalyzer.getSolvedSlots(source.cubeState(), source.orientation()).contains(F2LSlot.FL),
                    () -> "case-2.3 did not solve FL in frame=" + frameKey);
        }
    }

    @Test
    void rotatedSetupFamilies_shouldRemainDistinctWithoutAnInsertSlotKey() {
        var database = F2LSetupCaseDatabase.seedCases();
        assertDistinctSetupSignatures(database, List.of("case-17.1", "case-17.2", "case-17.3"));
        assertDistinctSetupSignatures(database, List.of("case-1.1", "case-1.2"));
        assertAllSetupVariantsRegistered(database, List.of("case-6.1", "case-6.2", "case-6.3", "case-6.4"));
    }

    private static void assertAllSetupVariantsRegistered(
            F2LSetupCaseDatabase database,
            List<String> names
    ) {
        for (var name : names) {
            assertTrue(database.allCases().stream().anyMatch(candidate -> candidate.name().equals(name)),
                    () -> "missing setup variant " + name);
        }
        var third = database.allCases().stream()
                .filter(candidate -> candidate.name().equals("case-6.3"))
                .findFirst().orElseThrow();
        var fourth = database.allCases().stream()
                .filter(candidate -> candidate.name().equals("case-6.4"))
                .findFirst().orElseThrow();
        if (third.signature().equals(fourth.signature())) {
            var compatible = database.findCompatible(F2LPreservationMask.empty(), third.signature());
            assertTrue(compatible.stream().anyMatch(candidate -> candidate.name().equals("case-6.3")));
            assertTrue(compatible.stream().anyMatch(candidate -> candidate.name().equals("case-6.4")));
        }
    }

    private static void assertDistinctSetupSignatures(
            F2LSetupCaseDatabase database,
            List<String> names
    ) {
        var signatures = new HashSet<cfop.F2LSetupSignature>();
        for (var name : names) {
            var setupCase = database.allCases().stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst()
                    .orElseThrow();
            assertTrue(signatures.add(setupCase.signature()),
                    () -> "duplicate setup signature for " + name + ": " + setupCase.signature()
                            + " existing=" + signatures);
        }
    }

    private static void canonicalizeVisibleFrame(CubeState raw, cube.CubeOrientation frame) {
        List<Move> inverse = List.of();
        var queue = new ArrayDeque<FramePath>();
        var visited = new HashSet<cube.CubeOrientationKey>();
        var identity = cube.CubeOrientationKey.from(new cube.CubeOrientation());
        var start = cube.CubeOrientationKey.from(frame);
        queue.add(new FramePath(start, List.of()));
        visited.add(start);
        while (!queue.isEmpty()) {
            var path = queue.removeFirst();
            if (path.key().equals(identity)) {
                inverse = path.moves();
                break;
            }
            for (var rotation : List.of(Move.X, Move.Y, Move.Z)) {
                var next = path.key().toOrientation();
                next.applyRotation(rotation);
                var key = cube.CubeOrientationKey.from(next);
                if (visited.add(key)) {
                    var moves = new ArrayList<>(path.moves());
                    moves.add(rotation);
                    queue.addLast(new FramePath(key, moves));
                }
            }
        }
        assertTrue(!inverse.isEmpty() || start.equals(identity), "could not find inverse frame for " + frame);
        MoveApplier.applyMoves(raw, inverse);
    }

    private record FramePath(cube.CubeOrientationKey key, List<Move> moves) {
    }

    private static F2LCaseSignature referenceSignature(CubeState cube, F2LSlot slot, CubeOrientation frame) {
        var targetCorner = targetCorner(slot, frame);
        var targetEdge = targetEdge(slot, frame);
        var rawCorner = findCornerPosition(cube, targetCorner);
        var rawEdge = findEdgePosition(cube, targetEdge);
        var facelets = CubeConverter.toFaceletStateAllowingCenterParity(cube);

        return new F2LCaseSignature(
                logicalCornerAt(rawCorner, frame),
                cornerOrientation(facelets, rawCorner, targetCorner, frame),
                logicalEdgeAt(rawEdge, frame),
                edgeOrientation(facelets, rawEdge, targetEdge, frame)
        );
    }

    private static void assertSuppliedScrambleCompletesF2l(Face crossFace) {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, SUPPLIED_SCRAMBLE);

        var cross = new CrossSolver().solve(scrambled, crossFace);
        var postCross = new OrientedCube(scrambled.copy());
        postCross.applyMoves(cross.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(postCross.cubeState(), postCross.orientation()), crossFace + " cross");

        var f2l = new F2LSolver().solveTrace(new OrientedCube(
                postCross.cubeState().copy(), postCross.orientation()
        )).algorithm();
        var publicAlgorithm = cross.concat(f2l);
        var publicReplay = scrambled.copy();
        MoveApplier.applyMoves(publicReplay, publicAlgorithm.getMoves());

        var expected = new OrientedCube(scrambled.copy());
        expected.applyMoves(publicAlgorithm.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(expected.cubeState(), expected.orientation()),
                crossFace + " cross after public algorithm " + publicAlgorithm);
        assertTrue(F2LAnalyzer.isF2LSolved(expected.cubeState(), expected.orientation()),
                crossFace + " F2L after public algorithm " + publicAlgorithm);

        canonicalizeVisibleFrame(publicReplay, expected.orientation());
        assertArrayEquals(expected.cubeState().cornerPerm, publicReplay.cornerPerm,
                crossFace + " public corner permutation");
        assertArrayEquals(expected.cubeState().cornerOri, publicReplay.cornerOri,
                crossFace + " public corner orientation");
        assertArrayEquals(expected.cubeState().edgePerm, publicReplay.edgePerm,
                crossFace + " public edge permutation");
        assertArrayEquals(expected.cubeState().edgeOri, publicReplay.edgeOri,
                crossFace + " public edge orientation");
    }

    private static int cornerOrientation(
            FaceletState facelets,
            Corner rawPosition,
            Corner targetCorner,
            CubeOrientation frame
    ) {
        var logicalPositionFaces = cornerFaces(logicalCornerAt(rawPosition, frame));
        var logicalTargetUpDownColor = frame.logicalFaceOf(cornerFaces(targetCorner)[0]);
        var refs = CORNER_STICKERS[rawPosition.ordinal()];
        for (var index = 0; index < refs.length; index++) {
            var sticker = refs[index];
            var logicalPositionFace = frame.logicalFaceOf(sticker.face());
            var logicalColor = frame.logicalFaceOf(facelets.getSticker(sticker.face(), sticker.index()));
            if (logicalColor == logicalTargetUpDownColor) {
                for (var orientation = 0; orientation < logicalPositionFaces.length; orientation++) {
                    if (logicalPositionFaces[orientation] == logicalPositionFace) {
                        return orientation;
                    }
                }
            }
        }
        throw new IllegalStateException("Target corner U/D sticker was not found at " + rawPosition);
    }

    private static int edgeOrientation(FaceletState facelets, Edge rawPosition, Edge targetEdge, CubeOrientation frame) {
        var logicalPositionFaces = edgeFaces(logicalEdgeAt(rawPosition, frame));
        var logicalTargetFirstColor = frame.logicalFaceOf(edgeFaces(targetEdge)[0]);
        var refs = EDGE_STICKERS[rawPosition.ordinal()];
        for (var index = 0; index < refs.length; index++) {
            var sticker = refs[index];
            var logicalPositionFace = frame.logicalFaceOf(sticker.face());
            var logicalColor = frame.logicalFaceOf(facelets.getSticker(sticker.face(), sticker.index()));
            if (logicalColor == logicalTargetFirstColor) {
                return logicalPositionFace == logicalPositionFaces[0] ? 0 : 1;
            }
        }
        throw new IllegalStateException("Target edge first sticker was not found at " + rawPosition);
    }

    private static Corner targetCorner(F2LSlot slot, CubeOrientation frame) {
        return switch (slot) {
            case FR -> cornerForFaces(frame.faceAt(Face.D), frame.faceAt(Face.F), frame.faceAt(Face.R));
            case FL -> cornerForFaces(frame.faceAt(Face.D), frame.faceAt(Face.F), frame.faceAt(Face.L));
            case BL -> cornerForFaces(frame.faceAt(Face.D), frame.faceAt(Face.B), frame.faceAt(Face.L));
            case BR -> cornerForFaces(frame.faceAt(Face.D), frame.faceAt(Face.B), frame.faceAt(Face.R));
        };
    }

    private static Edge targetEdge(F2LSlot slot, CubeOrientation frame) {
        return switch (slot) {
            case FR -> edgeForFaces(frame.faceAt(Face.F), frame.faceAt(Face.R));
            case FL -> edgeForFaces(frame.faceAt(Face.F), frame.faceAt(Face.L));
            case BL -> edgeForFaces(frame.faceAt(Face.B), frame.faceAt(Face.L));
            case BR -> edgeForFaces(frame.faceAt(Face.B), frame.faceAt(Face.R));
        };
    }

    private static Corner logicalCornerAt(Corner rawPosition, CubeOrientation frame) {
        var faces = cornerFaces(rawPosition);
        return cornerForFaces(
                frame.logicalFaceOf(faces[0]),
                frame.logicalFaceOf(faces[1]),
                frame.logicalFaceOf(faces[2])
        );
    }

    private static Edge logicalEdgeAt(Edge rawPosition, CubeOrientation frame) {
        var faces = edgeFaces(rawPosition);
        return edgeForFaces(frame.logicalFaceOf(faces[0]), frame.logicalFaceOf(faces[1]));
    }

    private static Corner findCornerPosition(CubeState cube, Corner target) {
        for (var position : Corner.values()) {
            if (cube.cornerPerm[position.ordinal()] == target.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing corner " + target);
    }

    private static Edge findEdgePosition(CubeState cube, Edge target) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == target.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing edge " + target);
    }

    private static Face[] cornerFaces(Corner corner) {
        return switch (corner) {
            case URF -> new Face[]{Face.U, Face.R, Face.F};
            case UFL -> new Face[]{Face.U, Face.F, Face.L};
            case ULB -> new Face[]{Face.U, Face.L, Face.B};
            case UBR -> new Face[]{Face.U, Face.B, Face.R};
            case DFR -> new Face[]{Face.D, Face.F, Face.R};
            case DLF -> new Face[]{Face.D, Face.L, Face.F};
            case DBL -> new Face[]{Face.D, Face.B, Face.L};
            case DRB -> new Face[]{Face.D, Face.R, Face.B};
        };
    }

    private static Edge edgeForFaces(Face first, Face second) {
        for (var edge : Edge.values()) {
            var faces = edgeFaces(edge);
            if ((faces[0] == first && faces[1] == second) || (faces[0] == second && faces[1] == first)) {
                return edge;
            }
        }
        throw new IllegalArgumentException("Faces do not form an edge: " + first + ", " + second);
    }

    private static Corner cornerForFaces(Face first, Face second, Face third) {
        for (var corner : Corner.values()) {
            var faces = cornerFaces(corner);
            if (contains(faces, first) && contains(faces, second) && contains(faces, third)) {
                return corner;
            }
        }
        throw new IllegalArgumentException("Faces do not form a corner: " + first + ", " + second + ", " + third);
    }

    private static boolean contains(Face[] faces, Face target) {
        for (var face : faces) {
            if (face == target) {
                return true;
            }
        }
        return false;
    }

    private static Face[] edgeFaces(Edge edge) {
        return switch (edge) {
            case UR -> new Face[]{Face.U, Face.R};
            case UF -> new Face[]{Face.U, Face.F};
            case UL -> new Face[]{Face.U, Face.L};
            case UB -> new Face[]{Face.U, Face.B};
            case DR -> new Face[]{Face.D, Face.R};
            case DF -> new Face[]{Face.D, Face.F};
            case DL -> new Face[]{Face.D, Face.L};
            case DB -> new Face[]{Face.D, Face.B};
            case FR -> new Face[]{Face.F, Face.R};
            case FL -> new Face[]{Face.F, Face.L};
            case BL -> new Face[]{Face.B, Face.L};
            case BR -> new Face[]{Face.B, Face.R};
        };
    }

    private static StickerRef sticker(Face face, int index) {
        return new StickerRef(face, index);
    }

    private record StickerRef(Face face, int index) {
    }
}
