package io;

import cube.Algorithm;
import cube.Move;

import java.util.List;

public final class ScrambleParser {
    private ScrambleParser() {
    }

    public static Algorithm parse(String scramble) {
        return Algorithm.parse(scramble);
    }

    public static List<Move> parseMoves(String scramble) {
        return parse(scramble).getMoves();
    }

    public static boolean isValid(String scramble) {
        try {
            parse(scramble);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
