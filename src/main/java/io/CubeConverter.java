package io;

import cube.Corner;
import cube.CubeState;
import cube.Edge;
import cube.Face;

public final class CubeConverter {
    private static final StickerRef[][] CORNER_FACELETS = {
            {sticker(Face.U, 8), sticker(Face.R, 0), sticker(Face.F, 2)}, // URF
            {sticker(Face.U, 6), sticker(Face.F, 0), sticker(Face.L, 2)}, // UFL
            {sticker(Face.U, 0), sticker(Face.L, 0), sticker(Face.B, 2)}, // ULB
            {sticker(Face.U, 2), sticker(Face.B, 0), sticker(Face.R, 2)}, // UBR
            {sticker(Face.D, 2), sticker(Face.F, 8), sticker(Face.R, 6)}, // DFR
            {sticker(Face.D, 0), sticker(Face.L, 8), sticker(Face.F, 6)}, // DLF
            {sticker(Face.D, 6), sticker(Face.B, 8), sticker(Face.L, 6)}, // DBL
            {sticker(Face.D, 8), sticker(Face.R, 8), sticker(Face.B, 6)}  // DRB
    };

    private static final Face[][] CORNER_COLORS = {
            {Face.U, Face.R, Face.F}, // URF
            {Face.U, Face.F, Face.L}, // UFL
            {Face.U, Face.L, Face.B}, // ULB
            {Face.U, Face.B, Face.R}, // UBR
            {Face.D, Face.F, Face.R}, // DFR
            {Face.D, Face.L, Face.F}, // DLF
            {Face.D, Face.B, Face.L}, // DBL
            {Face.D, Face.R, Face.B}  // DRB
    };

    private static final StickerRef[][] EDGE_FACELETS = {
            {sticker(Face.U, 5), sticker(Face.R, 1)}, // UR
            {sticker(Face.U, 7), sticker(Face.F, 1)}, // UF
            {sticker(Face.U, 3), sticker(Face.L, 1)}, // UL
            {sticker(Face.U, 1), sticker(Face.B, 1)}, // UB
            {sticker(Face.D, 5), sticker(Face.R, 7)}, // DR
            {sticker(Face.D, 1), sticker(Face.F, 7)}, // DF
            {sticker(Face.D, 3), sticker(Face.L, 7)}, // DL
            {sticker(Face.D, 7), sticker(Face.B, 7)}, // DB
            {sticker(Face.F, 5), sticker(Face.R, 3)}, // FR
            {sticker(Face.F, 3), sticker(Face.L, 5)}, // FL
            {sticker(Face.B, 5), sticker(Face.L, 3)}, // BL
            {sticker(Face.B, 3), sticker(Face.R, 5)}  // BR
    };

    private static final Face[][] EDGE_COLORS = {
            {Face.U, Face.R}, // UR
            {Face.U, Face.F}, // UF
            {Face.U, Face.L}, // UL
            {Face.U, Face.B}, // UB
            {Face.D, Face.R}, // DR
            {Face.D, Face.F}, // DF
            {Face.D, Face.L}, // DL
            {Face.D, Face.B}, // DB
            {Face.F, Face.R}, // FR
            {Face.F, Face.L}, // FL
            {Face.B, Face.L}, // BL
            {Face.B, Face.R}  // BR
    };

    private CubeConverter() {
    }

    public static CubeState toCubeState(FaceletState facelets) {
        CubeValidator.validate(facelets);

        CubeState cube = new CubeState();
        boolean[] seenCorners = new boolean[Corner.values().length];
        boolean[] seenEdges = new boolean[Edge.values().length];

        for (Corner position : Corner.values()) {
            Face[] stickers = readCornerStickers(facelets, position);
            int cubie = findCornerCubie(stickers);
            if (seenCorners[cubie]) {
                throw new IllegalArgumentException("Duplicate corner cubie detected: " + Corner.values()[cubie]);
            }

            cube.cornerPerm[position.ordinal()] = (byte) cubie;
            cube.cornerOri[position.ordinal()] = determineCornerOrientation(stickers, cubie);
            seenCorners[cubie] = true;
        }

        for (Edge position : Edge.values()) {
            Face[] stickers = readEdgeStickers(facelets, position);
            int cubie = findEdgeCubie(stickers);
            if (seenEdges[cubie]) {
                throw new IllegalArgumentException("Duplicate edge cubie detected: " + Edge.values()[cubie]);
            }

            cube.edgePerm[position.ordinal()] = (byte) cubie;
            cube.edgeOri[position.ordinal()] = determineEdgeOrientation(stickers, cubie);
            seenEdges[cubie] = true;
        }

        validateCubieState(cube);
        return cube;
    }

    public static FaceletState toFaceletState(CubeState cube) {
        validateCubieState(cube);

        FaceletState facelets = new FaceletState();

        for (Corner position : Corner.values()) {
            Corner cubie = Corner.values()[cube.cornerPerm[position.ordinal()]];
            int orientation = cube.cornerOri[position.ordinal()];
            Face[] colors = CORNER_COLORS[cubie.ordinal()];
            StickerRef[] refs = CORNER_FACELETS[position.ordinal()];

            for (int i = 0; i < refs.length; i++) {
                facelets = facelets.withSticker(refs[i].face, refs[i].index, colors[mod(i - orientation, 3)]);
            }
        }

        for (Edge position : Edge.values()) {
            Edge cubie = Edge.values()[cube.edgePerm[position.ordinal()]];
            int orientation = cube.edgeOri[position.ordinal()];
            Face[] colors = EDGE_COLORS[cubie.ordinal()];
            StickerRef[] refs = EDGE_FACELETS[position.ordinal()];

            for (int i = 0; i < refs.length; i++) {
                facelets = facelets.withSticker(refs[i].face, refs[i].index, colors[mod(i - orientation, 2)]);
            }
        }

        return facelets;
    }

    private static Face[] readCornerStickers(FaceletState facelets, Corner position) {
        StickerRef[] refs = CORNER_FACELETS[position.ordinal()];
        return new Face[]{
                facelets.getSticker(refs[0].face, refs[0].index),
                facelets.getSticker(refs[1].face, refs[1].index),
                facelets.getSticker(refs[2].face, refs[2].index)
        };
    }

    private static Face[] readEdgeStickers(FaceletState facelets, Edge position) {
        StickerRef[] refs = EDGE_FACELETS[position.ordinal()];
        return new Face[]{
                facelets.getSticker(refs[0].face, refs[0].index),
                facelets.getSticker(refs[1].face, refs[1].index)
        };
    }

    private static int findCornerCubie(Face[] stickers) {
        for (Corner cubie : Corner.values()) {
            if (sameColors(stickers, CORNER_COLORS[cubie.ordinal()])) {
                return cubie.ordinal();
            }
        }
        throw new IllegalArgumentException("Invalid corner stickers: " + stickers[0] + stickers[1] + stickers[2]);
    }

    private static int findEdgeCubie(Face[] stickers) {
        for (Edge cubie : Edge.values()) {
            if (sameColors(stickers, EDGE_COLORS[cubie.ordinal()])) {
                return cubie.ordinal();
            }
        }
        throw new IllegalArgumentException("Invalid edge stickers: " + stickers[0] + stickers[1]);
    }

    private static byte determineCornerOrientation(Face[] stickers, int cubie) {
        Face upDownColor = CORNER_COLORS[cubie][0];
        for (byte orientation = 0; orientation < 3; orientation++) {
            if (stickers[orientation] == upDownColor) {
                return orientation;
            }
        }
        throw new IllegalArgumentException("Corner cubie is missing its U/D sticker");
    }

    private static byte determineEdgeOrientation(Face[] stickers, int cubie) {
        return (byte) (stickers[0] == EDGE_COLORS[cubie][0] ? 0 : 1);
    }

    private static boolean sameColors(Face[] first, Face[] second) {
        if (first.length != second.length) {
            return false;
        }

        boolean[] matched = new boolean[second.length];
        for (Face face : first) {
            boolean found = false;
            for (int i = 0; i < second.length; i++) {
                if (!matched[i] && second[i] == face) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static void validateCubieState(CubeState cube) {
        if (cube == null) {
            throw new IllegalArgumentException("CubeState cannot be null");
        }
        if (cube.cornerPerm == null || cube.cornerPerm.length != Corner.values().length) {
            throw new IllegalArgumentException("CubeState must contain 8 corner permutation entries");
        }
        if (cube.cornerOri == null || cube.cornerOri.length != Corner.values().length) {
            throw new IllegalArgumentException("CubeState must contain 8 corner orientation entries");
        }
        if (cube.edgePerm == null || cube.edgePerm.length != Edge.values().length) {
            throw new IllegalArgumentException("CubeState must contain 12 edge permutation entries");
        }
        if (cube.edgeOri == null || cube.edgeOri.length != Edge.values().length) {
            throw new IllegalArgumentException("CubeState must contain 12 edge orientation entries");
        }

        boolean[] seenCorners = new boolean[Corner.values().length];
        int cornerTwistSum = 0;
        for (int i = 0; i < cube.cornerPerm.length; i++) {
            int cubie = cube.cornerPerm[i];
            int orientation = cube.cornerOri[i];
            if (cubie < 0 || cubie >= Corner.values().length) {
                throw new IllegalArgumentException("Invalid corner cubie index at position " + i + ": " + cubie);
            }
            if (seenCorners[cubie]) {
                throw new IllegalArgumentException("Duplicate corner cubie index: " + cubie);
            }
            if (orientation < 0 || orientation > 2) {
                throw new IllegalArgumentException("Invalid corner orientation at position " + i + ": " + orientation);
            }
            seenCorners[cubie] = true;
            cornerTwistSum += orientation;
        }
        if (cornerTwistSum % 3 != 0) {
            throw new IllegalArgumentException("Corner orientation sum must be divisible by 3");
        }

        boolean[] seenEdges = new boolean[Edge.values().length];
        int edgeFlipSum = 0;
        for (int i = 0; i < cube.edgePerm.length; i++) {
            int cubie = cube.edgePerm[i];
            int orientation = cube.edgeOri[i];
            if (cubie < 0 || cubie >= Edge.values().length) {
                throw new IllegalArgumentException("Invalid edge cubie index at position " + i + ": " + cubie);
            }
            if (seenEdges[cubie]) {
                throw new IllegalArgumentException("Duplicate edge cubie index: " + cubie);
            }
            if (orientation < 0 || orientation > 1) {
                throw new IllegalArgumentException("Invalid edge orientation at position " + i + ": " + orientation);
            }
            seenEdges[cubie] = true;
            edgeFlipSum += orientation;
        }
        if (edgeFlipSum % 2 != 0) {
            throw new IllegalArgumentException("Edge orientation sum must be even");
        }

        if (permutationParity(cube.cornerPerm) != permutationParity(cube.edgePerm)) {
            throw new IllegalArgumentException("Corner and edge permutation parity must match");
        }
    }

    private static int permutationParity(byte[] permutation) {
        int inversions = 0;
        for (int i = 0; i < permutation.length; i++) {
            for (int j = i + 1; j < permutation.length; j++) {
                if (permutation[i] > permutation[j]) {
                    inversions++;
                }
            }
        }
        return inversions % 2;
    }

    private static StickerRef sticker(Face face, int index) {
        return new StickerRef(face, index);
    }

    private static int mod(int value, int modulus) {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    private static final class StickerRef {
        private final Face face;
        private final int index;

        private StickerRef(Face face, int index) {
            this.face = face;
            this.index = index;
        }
    }
}
