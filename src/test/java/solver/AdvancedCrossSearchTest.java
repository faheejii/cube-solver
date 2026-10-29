package solver;

import cfop.CrossAnalyzer;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedCrossSearchTest {
    @Test
    void advancedCandidatesReplayAsCrossAndTheRequestedPairGoalInEveryFrame() {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, "R D R' D2 R D' R'");
        for (var face : Face.values()) {
            var result = new CrossSolver().solveOptimizedCross(scrambled, face);
            assertTrue(result != null, "advanced search should find a bounded candidate for " + face);
            if (face == Face.D) assertEquals(AdvancedCrossSearch.Goal.XXCROSS, result.goal());
            var replay = new OrientedCube(scrambled.copy());
            replay.applyMoves(result.algorithm().getMoves());
            assertTrue(CrossAnalyzer.isCrossSolved(replay.cubeState(), replay.orientation()),
                    face + " cross: " + result.algorithm());
            var requiredPairs = result.goal() == AdvancedCrossSearch.Goal.XXCROSS ? 2 : 1;
            assertTrue(result.algorithm().getMoveCount() <= (requiredPairs == 2 ? 10 : 8));
            assertTrue(cfop.F2LAnalyzer.countSolvedSlots(replay.cubeState(), replay.orientation()) >= requiredPairs,
                    face + " " + result.goal() + " candidate failed pair goal: " + result.algorithm());
        }
    }

    @Test
    void optimizedColorNeutralRanksAdvancedCrossCandidatesAcrossAllFaces() {
        var scramble = "D B2 R2 B' L' B2 D R2 F D2 R2 F' D2 R2 F U2 F2 R2 B' U' R'";
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, scramble);
        var expected = new EnumMap<Face, cube.Algorithm>(Face.class);
        var regular = new EnumMap<Face, cube.Algorithm>(Face.class);
        var crossSolver = new CrossSolver();
        for (var face : Face.values()) {
            var advanced = crossSolver.solveOptimizedCross(scrambled, face);
            expected.put(face, advanced == null ? crossSolver.solve(scrambled, face) : advanced.algorithm());
            regular.put(face, crossSolver.solve(scrambled, face));
        }

        var rankedFaces = List.of(Face.values()).stream()
                .sorted(Comparator.comparingInt((Face face) -> expected.get(face).getMoveCount())
                        .thenComparingInt(Face::ordinal))
                .limit(3)
                .toList();
        assertFalse(rankedFaces.stream().allMatch(face -> expected.get(face).equals(regular.get(face))),
                "fixture should distinguish advanced candidates from regular crosses");

        var result = new CfopSolveService().solve(
                CfopSolveRequest.colorNeutral(scramble, F2LMode.OPTIMIZED)
        );
        var selectedFace = Face.fromNotation(result.crossFace().charAt(0));
        assertTrue(rankedFaces.contains(selectedFace), "CN optimized should evaluate the top three advanced crosses");
        assertEquals(expected.get(selectedFace).toString(), result.cross().algorithm(),
                "CN optimized should use the advanced candidate for the selected face");
        assertTrue(result.fullySolved());
    }

    @Test
    void restrictedPolicyReturnsAValidNoBCandidateWhenItFindsOne() {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, "R U2 F' L D B2 R' F2 U L2 D' B U' F R2 D2");

        var result = AdvancedCrossSearch.solve(
                scrambled,
                Face.D,
                AdvancedCrossSearch.SearchPolicy.NO_B_FOUR_Y
        );
        assertTrue(result == null || result.algorithm().getMoveCount() <=
                (result.goal() == AdvancedCrossSearch.Goal.XXCROSS ? 10 : 8));
        if (result == null) return;

        assertTrue(result.algorithm().getMoves().stream().noneMatch(move ->
                        move == cube.Move.B || move == cube.Move.B2 || move == cube.Move.B_PRIME),
                "restricted candidate must not contain B turns: " + result.algorithm());
        assertTrue(result.algorithm().getMoves().stream().filter(cube.Move::isCubeRotation)
                        .allMatch(move -> move == cube.Move.Y || move == cube.Move.Y2 || move == cube.Move.Y_PRIME),
                "restricted candidate may use only a Y orientation: " + result.algorithm());
        assertTrue(result.algorithm().getMoves().stream().filter(cube.Move::isCubeRotation).count() <= 1,
                "candidate should use at most one leading Y rotation: " + result.algorithm());
        assertCandidateSolvesGoal(scrambled, result);
    }

    @Test
    void productionSearchMatchesExplicitUnrestrictedPolicy() {
        var scrambled = new CubeState();
        MoveApplier.applyAlgorithm(scrambled, "F R U2 B' L2 D F2 R' U L B2 D'");

        var production = AdvancedCrossSearch.solve(scrambled, Face.D);
        var explicit = AdvancedCrossSearch.solve(
                scrambled,
                Face.D,
                AdvancedCrossSearch.SearchPolicy.UNRESTRICTED
        );

        assertEquals(explicit.goal(), production.goal());
        assertEquals(explicit.algorithm().toString(), production.algorithm().toString());
    }

    private static void assertCandidateSolvesGoal(CubeState scrambled, AdvancedCrossSearch.SearchResult result) {
        var replay = new OrientedCube(scrambled.copy());
        replay.applyMoves(result.algorithm().getMoves());
        assertTrue(CrossAnalyzer.isCrossSolved(replay.cubeState(), replay.orientation()), result.algorithm().toString());
        var requiredPairs = result.goal() == AdvancedCrossSearch.Goal.XXCROSS ? 2 : 1;
        assertTrue(cfop.F2LAnalyzer.countSolvedSlots(replay.cubeState(), replay.orientation()) >= requiredPairs,
                result.goal() + " candidate failed pair goal: " + result.algorithm());
    }
}
