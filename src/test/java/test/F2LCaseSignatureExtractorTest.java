package test;

import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry;
import cfop.F2LSlot;
import cube.CubeOrientation;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.Edge;
import cube.Face;
import cube.MoveApplier;
import io.CubeConverter;
import io.FaceletState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class F2LCaseSignatureExtractorTest {
    private static final StickerRef[] FIRST_EDGE_STICKERS = {
            sticker(Face.U, 5), sticker(Face.U, 7), sticker(Face.U, 3), sticker(Face.U, 1),
            sticker(Face.D, 5), sticker(Face.D, 1), sticker(Face.D, 3), sticker(Face.D, 7),
            sticker(Face.F, 5), sticker(Face.F, 3), sticker(Face.B, 5), sticker(Face.B, 3)
    };
    private static final StickerRef[][] EDGE_STICKERS = {
            {sticker(Face.U, 5), sticker(Face.R, 1)}, {sticker(Face.U, 7), sticker(Face.F, 1)},
            {sticker(Face.U, 3), sticker(Face.L, 1)}, {sticker(Face.U, 1), sticker(Face.B, 1)},
            {sticker(Face.D, 5), sticker(Face.R, 7)}, {sticker(Face.D, 1), sticker(Face.F, 7)},
            {sticker(Face.D, 3), sticker(Face.L, 7)}, {sticker(Face.D, 7), sticker(Face.B, 7)},
            {sticker(Face.F, 5), sticker(Face.R, 3)}, {sticker(Face.F, 3), sticker(Face.L, 5)},
            {sticker(Face.B, 5), sticker(Face.L, 3)}, {sticker(Face.B, 3), sticker(Face.R, 5)}
    };

    @Test
    void directEdgeOrientation_shouldMatchFaceletReferenceInEveryFrame() {
        var scrambles = List.of(
                "",
                "R U R' U' F2 D L2 B'",
                "B R' F U D' R D' R2 B U2 R U2 L' D2 R F2 R2 D2 R B2 R2",
                "x R2 y U' z F L2 D' B2 R U2"
        );

        for (var scramble : scrambles) {
            var cube = new CubeState();
            MoveApplier.applyAlgorithm(cube, scramble);
            for (var orientationKey : CubeOrientationKey.all()) {
                var orientation = orientationKey.toOrientation();
                for (var slot : F2LSlot.values()) {
                    var actual = F2LCaseSignatureExtractor.extract(cube, slot, orientation);
                    assertEquals(
                            referenceEdgeOrientation(cube, slot, orientation),
                            actual.edgeOrientation(),
                            () -> "scramble=" + scramble + " frame=" + orientationKey + " slot=" + slot
                    );
                }
            }
        }
    }

    private static int referenceEdgeOrientation(CubeState cube, F2LSlot slot, CubeOrientation orientation) {
        var targetEdge = F2LGeometry.targetSlotFor(slot, orientation).edge();
        var rawPosition = findEdgePosition(cube, targetEdge);
        FaceletState facelets = CubeConverter.toFaceletStateAllowingCenterParity(cube);
        var logicalPositionEdge = edgeForFaces(
                orientation.logicalFaceOf(edgeFaces(rawPosition)[0]),
                orientation.logicalFaceOf(edgeFaces(rawPosition)[1])
        );
        var logicalTargetFirstColor = orientation.logicalFaceOf(edgeFaces(targetEdge)[0]);
        for (var sticker : EDGE_STICKERS[rawPosition.ordinal()]) {
            var logicalColor = orientation.logicalFaceOf(facelets.getSticker(sticker.face(), sticker.index()));
            if (logicalColor == logicalTargetFirstColor) {
                return orientation.logicalFaceOf(sticker.face()) == edgeFaces(logicalPositionEdge)[0] ? 0 : 1;
            }
        }
        throw new IllegalStateException("Missing target sticker");
    }

    private static Edge findEdgePosition(CubeState cube, Edge target) {
        for (var position : Edge.values()) {
            if (cube.edgePerm[position.ordinal()] == target.ordinal()) {
                return position;
            }
        }
        throw new IllegalStateException("Missing edge " + target);
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

    private static Edge edgeForFaces(Face first, Face second) {
        for (var edge : Edge.values()) {
            var faces = edgeFaces(edge);
            if ((faces[0] == first && faces[1] == second) || (faces[0] == second && faces[1] == first)) {
                return edge;
            }
        }
        throw new IllegalArgumentException("Faces do not form an edge: " + first + ", " + second);
    }

    private static StickerRef sticker(Face face, int index) {
        return new StickerRef(face, index);
    }

    private record StickerRef(Face face, int index) {
    }
}
