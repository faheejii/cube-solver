package io;

import cube.Face;

import java.util.Arrays;
import java.util.Objects;

public record FaceletState(Face[][] stickers) {
    public static final int FACE_COUNT = Face.values().length;
    public static final int FACELET_COUNT_PER_FACE = 9;
    public static final int TOTAL_FACELETS = FACE_COUNT * FACELET_COUNT_PER_FACE;

    public FaceletState() {
        this(createSolvedStickers());
    }

    public FaceletState(Face[][] stickers) {
        validateShape(stickers);
        this.stickers = copyStickers(stickers);
    }

    public static FaceletState fromNotation(String notation) {
        if (notation == null || notation.length() != TOTAL_FACELETS) {
            throw new IllegalArgumentException("Facelet notation must contain exactly 54 characters");
        }

        Face[][] stickers = new Face[FACE_COUNT][FACELET_COUNT_PER_FACE];
        for (int i = 0; i < notation.length(); i++) {
            stickers[i / FACELET_COUNT_PER_FACE][i % FACELET_COUNT_PER_FACE] = Face.fromNotation(notation.charAt(i));
        }
        return new FaceletState(stickers);
    }

    public Face getSticker(Face face, int index) {
        validateIndex(index);
        return stickers[face.ordinal()][index];
    }

    public FaceletState withSticker(Face face, int index, Face value) {
        validateIndex(index);
        if (value == null) {
            throw new IllegalArgumentException("Sticker value cannot be null");
        }
        Face[][] updated = copyStickers(stickers);
        updated[face.ordinal()][index] = value;
        return new FaceletState(updated);
    }

    public Face[] getFace(Face face) {
        return Arrays.copyOf(stickers[face.ordinal()], FACELET_COUNT_PER_FACE);
    }

    @Override
    public Face[][] stickers() {
        return copyStickers(stickers);
    }

    public FaceletState copy() {
        return new FaceletState(stickers);
    }

    public String toNotation() {
        StringBuilder builder = new StringBuilder(TOTAL_FACELETS);
        for (Face face : Face.values()) {
            for (Face sticker : stickers[face.ordinal()]) {
                builder.append(sticker.getNotation());
            }
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return toNotation();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FaceletState that)) {
            return false;
        }
        return Arrays.deepEquals(this.stickers, that.stickers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.deepHashCode(stickers));
    }

    private static Face[][] createSolvedStickers() {
        Face[][] solved = new Face[FACE_COUNT][FACELET_COUNT_PER_FACE];
        for (Face face : Face.values()) {
            Arrays.fill(solved[face.ordinal()], face);
        }
        return solved;
    }

    private static void validateShape(Face[][] stickers) {
        if (stickers == null || stickers.length != FACE_COUNT) {
            throw new IllegalArgumentException("FaceletState must contain exactly 6 faces");
        }

        for (Face[] faceStickers : stickers) {
            if (faceStickers == null || faceStickers.length != FACELET_COUNT_PER_FACE) {
                throw new IllegalArgumentException("Each face must contain exactly 9 stickers");
            }
            for (Face sticker : faceStickers) {
                if (sticker == null) {
                    throw new IllegalArgumentException("Sticker values cannot be null");
                }
            }
        }
    }

    private static Face[][] copyStickers(Face[][] source) {
        Face[][] copy = new Face[FACE_COUNT][FACELET_COUNT_PER_FACE];
        for (int i = 0; i < FACE_COUNT; i++) {
            copy[i] = Arrays.copyOf(source[i], FACELET_COUNT_PER_FACE);
        }
        return copy;
    }

    private static void validateIndex(int index) {
        if (index < 0 || index >= FACELET_COUNT_PER_FACE) {
            throw new IndexOutOfBoundsException("Facelet index must be between 0 and 8");
        }
    }
}
