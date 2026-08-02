package solver;

import cube.Algorithm;
import cube.Move;

import java.util.ArrayList;
import java.util.List;

public record F2LSolveTrace(
        List<Move> algorithmMoves,
        List<F2LPairStep> pairSteps,
        boolean solved
) {
    public F2LSolveTrace {
        algorithmMoves = immutableMoves(algorithmMoves, "algorithmMoves");
        pairSteps = pairSteps == null ? List.of() : List.copyOf(pairSteps);
    }

    public Algorithm algorithm() {
        return Algorithm.fromMoves(algorithmMoves);
    }

    public boolean hasCompletePairTrace() {
        if (pairSteps.size() != 4) {
            return false;
        }
        for (int i = 0; i < pairSteps.size(); i++) {
            if (pairSteps.get(i).order() != i + 1) {
                return false;
            }
        }
        return true;
    }

    public boolean algorithmMatchesPairSteps() {
        var reconstructed = new ArrayList<Move>();
        for (var pairStep : pairSteps) {
            reconstructed.addAll(pairStep.completeMoves());
        }
        return algorithmMoves.equals(reconstructed);
    }

    private static List<Move> immutableMoves(List<Move> moves, String name) {
        if (moves == null || moves.stream().anyMatch(move -> move == null)) {
            throw new IllegalArgumentException(name + " cannot be null or contain null moves");
        }
        return List.copyOf(moves);
    }
}
