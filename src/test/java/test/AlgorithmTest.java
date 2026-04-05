package test;

import cube.Algorithm;
import cube.Move;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AlgorithmTest {
    @Test
    void parse_shouldReadStandardNotation() {
        Algorithm algorithm = Algorithm.parse("R U R' U'");

        assertEquals(4, algorithm.getMoveCount());
        assertEquals(List.of(Move.R, Move.U, Move.R_PRIME, Move.U_PRIME), algorithm.getMoves());
        assertEquals("R U R' U'", algorithm.toString());
    }

    @Test
    void parse_shouldReadSliceAndCubeRotationNotation() {
        Algorithm algorithm = Algorithm.parse("M E2 S' x y2 z'");

        assertEquals(
                List.of(Move.M, Move.E2, Move.S_PRIME, Move.X, Move.Y2, Move.Z_PRIME),
                algorithm.getMoves()
        );
        assertEquals("M E2 S' x y2 z'", algorithm.toString());
    }

    @Test
    void parse_shouldRejectNullBlankAndInvalidMoves() {
        assertThrows(IllegalArgumentException.class, () -> Algorithm.parse(null));
        assertThrows(IllegalArgumentException.class, () -> Algorithm.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> Algorithm.parse("R X"));
    }

    @Test
    void inverse_shouldReverseOrderAndInvertEachMove() {
        Algorithm algorithm = Algorithm.parse("R U2 F'");

        Algorithm inverse = algorithm.inverse();

        assertEquals(List.of(Move.F, Move.U2, Move.R_PRIME), inverse.getMoves());
        assertEquals("F U2 R'", inverse.toString());
    }

    @Test
    void copy_shouldCreateIndependentAlgorithm() {
        Algorithm original = Algorithm.parse("R U");
        Algorithm copy = original.copy();

        copy.add(Move.F);

        assertEquals(List.of(Move.R, Move.U), original.getMoves());
        assertEquals(List.of(Move.R, Move.U, Move.F), copy.getMoves());
    }

    @Test
    void getLast_shouldThrowWhenAlgorithmIsEmpty() {
        Algorithm algorithm = new Algorithm();

        assertThrows(IllegalStateException.class, algorithm::getLast);
    }
}
