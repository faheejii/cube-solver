package io;

import cube.Face;

import java.util.EnumMap;
import java.util.Map;

public final class CubeValidator {
    private CubeValidator() {
    }

    public static void validate(FaceletState state) {
        validate(state, true);
    }

    public static void validate(FaceletState state, boolean enforceCenters) {
        if (state == null) {
            throw new IllegalArgumentException("FaceletState cannot be null");
        }

        if (enforceCenters) {
            validateCenters(state);
        }
        validateColorCounts(state);
    }

    public static boolean isValid(FaceletState state) {
        try {
            validate(state);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validateCenters(FaceletState state) {
        for (Face face : Face.values()) {
            Face center = state.getSticker(face, 4);
            if (center != face) {
                throw new IllegalArgumentException(
                        "Center sticker for face " + face + " must be " + face + " but was " + center
                );
            }
        }
    }

    private static void validateColorCounts(FaceletState state) {
        Map<Face, Integer> counts = new EnumMap<>(Face.class);
        for (Face face : Face.values()) {
            counts.put(face, 0);
        }

        for (Face face : Face.values()) {
            for (Face sticker : state.getFace(face)) {
                counts.put(sticker, counts.get(sticker) + 1);
            }
        }

        for (Face face : Face.values()) {
            int count = counts.get(face);
            if (count != FaceletState.FACELET_COUNT_PER_FACE) {
                throw new IllegalArgumentException(
                        "Face " + face + " must appear exactly 9 times but appeared " + count + " times"
                );
            }
        }
    }
}
