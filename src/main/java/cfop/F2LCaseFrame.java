package cfop;

import cube.Algorithm;
import cube.Move;

import java.util.List;

/**
 * Defines the canonical F2L case frame. All case-db algorithms will be authored
 * for the FR slot, and other slots should be normalized into that frame first.
 */
public final class F2LCaseFrame {
    public static final F2LSlot CANONICAL_SLOT = F2LSlot.FR;

    private F2LCaseFrame() {
    }

    public static Algorithm rotationToCanonical(F2LSlot slot) {
        return switch (slot) {
            case FR -> new Algorithm();
            case FL -> Algorithm.fromMoves(List.of(Move.Y_PRIME));
            case BL -> Algorithm.fromMoves(List.of(Move.Y2));
            case BR -> Algorithm.fromMoves(List.of(Move.Y));
        };
    }
}
