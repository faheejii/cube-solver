package cube;

import java.util.ArrayList;
import java.util.List;

public class Algorithm {
    private final List<Move> moves = new ArrayList<>();

    public static Algorithm parse(String algorithm) {
        var parsedMoves = new ArrayList<Move>();
        if (algorithm == null || algorithm.isBlank())
            throw new IllegalArgumentException("Algorithm cannot be null or blank");

        var splits = algorithm.trim().split("\\s+");

        for (var split : splits) {
            parsedMoves.add(Move.fromNotation(split));
        }
        return Algorithm.fromMoves(parsedMoves);
    }

    public static Algorithm fromMoves(List<Move> moves) {
        var algorithm = new Algorithm();
        algorithm.addAll(moves);
        return algorithm;
    }

    public List<Move> getMoves() {
        return List.copyOf(this.moves);
    }

    public int getMoveCount() {
        return moves.size();
    }

    public boolean isEmpty() {
        return moves.isEmpty();
    }

    public void add(Move move) {
        moves.add(move);
    }

    public void addAll(List<Move> moves) {
        this.moves.addAll(moves);
    }

    public Algorithm concat(Algorithm other) {
        var newAlgorithm = new Algorithm();
        newAlgorithm.addAll(this.moves);
        newAlgorithm.addAll(other.moves);
        return newAlgorithm;
    }

    public Algorithm inverse() {
        var inverseAlgorithm = new Algorithm();
        for (int i = moves.size() - 1; i >= 0; i--) {
            inverseAlgorithm.add(moves.get(i).inverse());
        }
        return inverseAlgorithm;
    }

    public Algorithm copy() {
        var copy = new Algorithm();
        copy.addAll(this.moves);
        return copy;
    }

    public Move getMove(int index) {
        return moves.get(index);
    }

    public Move getLast() {
        if (moves.isEmpty()) {
            throw new IllegalStateException("Algorithm is empty");
        }
        return moves.get(moves.size() - 1);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        for (var move : moves) {
            sb.append(move).append(' ');
        }
        return sb.toString().trim();
    }
}
