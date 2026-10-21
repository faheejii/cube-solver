package solver;

import cfop.CrossAnalyzer;
import cube.CubeState;
import cube.Face;
import cube.Move;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossSearchPreferencesTest {
    @Test
    void movePolicyIncludesOnlyOuterFaceTurnsAndHonorsBRestriction() {
        for (var avoidB : List.of(false, true)) {
            var options = new CrossSearchOptions(avoidB);
            var moves = CrossSearchMovePolicy.moves(options);
            assertEquals(avoidB ? 15 : 18, moves.length);
            assertFalse(java.util.Arrays.stream(moves).anyMatch(Move::isWideMove));
            if (avoidB) {
                assertFalse(java.util.Arrays.stream(moves).anyMatch(move -> faceFamily(move) == 5));
                assertEquals(4, CrossSearchMovePolicy.advancedYawTrials(options).size());
            } else {
                assertEquals(1, CrossSearchMovePolicy.advancedYawTrials(options).size());
            }
        }
    }

    @Test
    void fastAndAdvancedSearchNeverUseWideTurnsAndHonorBRestriction() {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, "R");
        for (var avoidB : List.of(false, true)) {
            var options = new CrossSearchOptions(avoidB);
            var fast = new CrossSolver().solve(scrambled, Face.D, options);
            assertCrossSolved(scrambled, fast, options, "Fast");

            var advanced = new CrossSolver().solveOptimizedCross(scrambled, Face.D, options);
            assertTrue(advanced != null, "Advanced should find a candidate for " + options);
            assertCrossSolved(scrambled, advanced.algorithm(), options, "Optimized");
            var faceTurnCount = (int) advanced.algorithm().getMoves().stream()
                    .filter(move -> !move.isCubeRotation()).count();
            assertTrue(faceTurnCount <= (advanced.goal() == AdvancedCrossSearch.Goal.XXCROSS ? 10 : 8),
                    "Advanced must respect its face-turn bound: " + advanced.algorithm());
        }
    }

    private static void assertCrossSolved(
            CubeState scrambled,
            cube.Algorithm algorithm,
            CrossSearchOptions options,
            String engine
    ) {
        assertTrue(algorithm.getMoves().stream().noneMatch(Move::isWideMove),
                engine + " used a wide turn: " + algorithm);
        if (options.avoidBTurns()) {
            assertTrue(algorithm.getMoves().stream().noneMatch(move -> faceFamily(move) == 5),
                    engine + " used a B turn while restricted: " + algorithm);
        }
        var replay = new OrientedCube(scrambled.copy());
        replay.applyMoves(algorithm.getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(replay.cubeState(), replay.orientation()),
                engine + " did not solve cross: " + algorithm);
    }

    private static int faceFamily(Move move) {
        if (move.isCubeRotation()) return -1;
        return switch (move) {
            case U, U2, U_PRIME -> 0;
            case R, R2, R_PRIME -> 1;
            case F, F2, F_PRIME -> 2;
            case D, D2, D_PRIME -> 3;
            case L, L2, L_PRIME -> 4;
            case B, B2, B_PRIME -> 5;
            default -> -1;
        };
    }
}
