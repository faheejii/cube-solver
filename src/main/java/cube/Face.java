package cube;

public enum Face {
    U('U'),
    R('R'),
    F('F'),
    D('D'),
    L('L'),
    B('B');

    private final char notation;

    Face(char notation) {
        this.notation = notation;
    }

    public char getNotation() {
        return notation;
    }

    public static Face fromNotation(char notation) {
        return switch (Character.toUpperCase(notation)) {
            case 'U' -> U;
            case 'R' -> R;
            case 'F' -> F;
            case 'D' -> D;
            case 'L' -> L;
            case 'B' -> B;
            default -> throw new IllegalArgumentException("Invalid face notation: " + notation);
        };
    }

    @Override
    public String toString() {
        return String.valueOf(notation);
    }
}
