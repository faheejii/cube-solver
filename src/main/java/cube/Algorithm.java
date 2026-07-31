package cube;

import java.util.ArrayList;
import java.util.List;

public class Algorithm {
    private final List<Move> moves = new ArrayList<>();

    public Algorithm() {
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

    /**
     * Rewrites rotations into the current logical face/slice moves. This is used
     * when loading last-layer algorithms, where runtime recognition is AUF-only.
     */
    public static Algorithm materializeCubeRotations(Algorithm algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return new Algorithm();
        }
        var orientation = new CubeOrientation();
        var materialized = new ArrayList<Move>();
        for (var move : algorithm.moves) {
            if (move.isCubeRotation()) {
                orientation.applyRotation(move);
            } else {
                materialized.add(orientation.mapMove(move));
            }
        }
        return normalize(Algorithm.fromMoves(materialized));
    }

    /**
     * Compiles center-preserving wide/slice notation to outer turns before
     * materializing the temporary cube rotations introduced by the rewrite.
     */
    public static Algorithm materializeWideAndSliceMoves(Algorithm algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            return new Algorithm();
        }

        var expanded = new ArrayList<Move>();
        for (var move : algorithm.moves) {
            var quarterTurn = quarterTurnExpansion(move);
            if (quarterTurn == null) {
                expanded.add(move);
                continue;
            }

            var turns = turnAmount(move);
            var replacement = turns == 3 ? quarterTurn.inverse() : quarterTurn;
            expanded.addAll(replacement.getMoves());
            if (turns == 2) {
                expanded.addAll(quarterTurn.getMoves());
            }
        }
        return materializeCubeRotations(Algorithm.fromMoves(expanded));
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
    }

    public void addAll(List<Move> moves) {
        this.moves.addAll(moves);
    }

    public Algorithm concat(Algorithm other) {
        var newAlgorithm = new Algorithm();
        newAlgorithm.moves.addAll(this.moves);
        newAlgorithm.moves.addAll(other.moves);
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
        copy.moves.addAll(this.moves);
        return copy;
    }

    public Move getMove(int index) {
        return moves.get(index);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        for (var move : moves) {
            sb.append(move).append(' ');
        }
        return sb.toString().trim();
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

    private static Algorithm quarterTurnExpansion(Move move) {
        var moves = switch (familyIndex(move)) {
            case 6 -> List.of(Move.Y, Move.D);                 // u = y D
            case 7 -> List.of(Move.X, Move.L);                 // r = x L
            case 8 -> List.of(Move.Z, Move.B);                 // f = z B
            case 9 -> List.of(Move.Y_PRIME, Move.U);           // d = y' U
            case 10 -> List.of(Move.X_PRIME, Move.R);          // l = x' R
            case 11 -> List.of(Move.Z_PRIME, Move.F);          // b = z' F
            case 12 -> List.of(Move.L_PRIME, Move.X_PRIME, Move.R); // M = L' x' R
            case 13 -> List.of(Move.U_PRIME, Move.Y, Move.D);  // E = U' y D
            case 14 -> List.of(Move.F_PRIME, Move.Z, Move.B);  // S = F' z B
            default -> null;
        };
        return moves == null ? null : Algorithm.fromMoves(moves);
    }
}
