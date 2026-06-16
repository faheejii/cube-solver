package cube;

import java.util.ArrayList;
import java.util.List;

public class Algorithm {
    private final List<Move> moves = new ArrayList<>();
    private String displayNotation;

    public Algorithm() {
        this(null);
    }

    private Algorithm(String displayNotation) {
        this.displayNotation = normalizeDisplay(displayNotation);
    }

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

    public static Algorithm fromMoves(List<Move> moves, String displayNotation) {
        var algorithm = new Algorithm();
        algorithm.moves.addAll(moves);
        algorithm.displayNotation = normalizeDisplay(displayNotation);
        return algorithm;
    }

    public static Algorithm normalize(Algorithm algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return new Algorithm();
        }

        var normalized = new ArrayList<Move>();
        for (var move : algorithm.moves) {
            var lastIndex = normalized.size() - 1;
            if (lastIndex >= 0 && sameMoveFamily(normalized.get(lastIndex), move)) {
                var combined = combine(normalized.remove(lastIndex), move);
                if (combined != null) {
                    normalized.add(combined);
                }
            } else {
                normalized.add(move);
            }
        }
        return Algorithm.fromMoves(normalized);
    }

    public List<Move> getMoves() {
        return List.copyOf(this.moves);
    }

    // Counts solving moves only. Ignore rotations like x, y, z and their variants.
    public int getMoveCount() {
        int count = 0;
        for (var move : this.moves) {
            if (!move.isCubeRotation()) {
                count++;
            }
        }
        return count;
    }

    public boolean isEmpty() {
        return moves.isEmpty();
    }

    public void add(Move move) {
        moves.add(move);
        displayNotation = null;
    }

    public void addAll(List<Move> moves) {
        this.moves.addAll(moves);
        displayNotation = null;
    }

    public Algorithm concat(Algorithm other) {
        var newAlgorithm = new Algorithm();
        newAlgorithm.moves.addAll(this.moves);
        newAlgorithm.moves.addAll(other.moves);
        newAlgorithm.displayNotation = joinDisplay(this.toString(), other.toString());
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
        var copy = new Algorithm(displayNotation);
        copy.moves.addAll(this.moves);
        return copy;
    }

    public Move getMove(int index) {
        return moves.get(index);
    }

    @Override
    public String toString() {
        if (displayNotation != null) {
            return displayNotation;
        }
        var sb = new StringBuilder();
        for (var move : moves) {
            sb.append(move).append(' ');
        }
        return sb.toString().trim();
    }

    private static String normalizeDisplay(String displayNotation) {
        if (displayNotation == null || displayNotation.isBlank()) {
            return null;
        }
        return displayNotation.trim();
    }

    private static String joinDisplay(String first, String second) {
        if (first == null || first.isBlank()) {
            return normalizeDisplay(second);
        }
        if (second == null || second.isBlank()) {
            return normalizeDisplay(first);
        }
        return first + " " + second;
    }

    private static boolean sameMoveFamily(Move first, Move second) {
        return familyIndex(first) == familyIndex(second);
    }

    private static Move combine(Move first, Move second) {
        var familyIndex = familyIndex(first);
        var turnAmount = (turnAmount(first) + turnAmount(second)) % 4;
        if (turnAmount == 0) {
            return null;
        }
        return Move.values()[familyIndex * 3 + moveOffset(turnAmount)];
    }

    private static int familyIndex(Move move) {
        return move.ordinal() / 3;
    }

    private static int turnAmount(Move move) {
        return switch (move.ordinal() % 3) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            default -> throw new IllegalStateException("Unexpected move ordinal: " + move);
        };
    }

    private static int moveOffset(int turnAmount) {
        return switch (turnAmount) {
            case 1 -> 0;
            case 2 -> 1;
            case 3 -> 2;
            default -> throw new IllegalArgumentException("Unexpected turn amount: " + turnAmount);
        };
    }
}
