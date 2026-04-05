package test;

import cube.Algorithm;
import cube.Move;
import io.ScrambleParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScrambleParserTest {
    @Test
    void parse_shouldReturnAlgorithmFromValidScramble() {
        Algorithm algorithm = ScrambleParser.parse("R U R' U'");

        assertEquals("R U R' U'", algorithm.toString());
        assertEquals(4, algorithm.getMoveCount());
    }

    @Test
    void parseMoves_shouldHandleExtraWhitespace() {
        List<Move> moves = ScrambleParser.parseMoves("  R   U2  F'  ");

        assertEquals(List.of(Move.R, Move.U2, Move.F_PRIME), moves);
    }

    @Test
    void parse_shouldRejectNullBlankAndInvalidMoves() {
        assertThrows(IllegalArgumentException.class, () -> ScrambleParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ScrambleParser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> ScrambleParser.parse("R X"));
    }

    @Test
    void isValid_shouldReflectWhetherScrambleCanBeParsed() {
        assertTrue(ScrambleParser.isValid("R U F2"));
        assertFalse(ScrambleParser.isValid("R X"));
        assertFalse(ScrambleParser.isValid(" "));
    }
}
