package cube;

public enum Move {
    U("U"), U2("U2"), U_PRIME("U'"),
    R("R"), R2("R2"), R_PRIME("R'"),
    F("F"), F2("F2"), F_PRIME("F'"),
    D("D"), D2("D2"), D_PRIME("D'"),
    L("L"), L2("L2"), L_PRIME("L'"),
    B("B"), B2("B2"), B_PRIME("B'"),
    M("M"), M2("M2"), M_PRIME("M'"),
    E("E"), E2("E2"), E_PRIME("E'"),
    S("S"), S2("S2"), S_PRIME("S'"),
    X("x"), X2("x2"), X_PRIME("x'"),
    Y("y"), Y2("y2"), Y_PRIME("y'"),
    Z("z"), Z2("z2"), Z_PRIME("z'");

    private final String notation;

    Move(String notation) {
        this.notation = notation;
    }

    public String getNotation() {
        return notation;
    }

    public Move inverse() {
        return switch (this) {
            case U -> U_PRIME;
            case U2 -> U2;
            case U_PRIME -> U;
            case R -> R_PRIME;
            case R2 -> R2;
            case R_PRIME -> R;
            case F -> F_PRIME;
            case F2 -> F2;
            case F_PRIME -> F;
            case D -> D_PRIME;
            case D2 -> D2;
            case D_PRIME -> D;
            case L -> L_PRIME;
            case L2 -> L2;
            case L_PRIME -> L;
            case B -> B_PRIME;
            case B2 -> B2;
            case B_PRIME -> B;
            case M -> M_PRIME;
            case M2 -> M2;
            case M_PRIME -> M;
            case E -> E_PRIME;
            case E2 -> E2;
            case E_PRIME -> E;
            case S -> S_PRIME;
            case S2 -> S2;
            case S_PRIME -> S;
            case X -> X_PRIME;
            case X2 -> X2;
            case X_PRIME -> X;
            case Y -> Y_PRIME;
            case Y2 -> Y2;
            case Y_PRIME -> Y;
            case Z -> Z_PRIME;
            case Z2 -> Z2;
            case Z_PRIME -> Z;
        };
    }

    public static Move fromNotation(String notation) {
        for (Move move : values()) {
            if (move.notation.equals(notation)) {
                return move;
            }
        }
        throw new IllegalArgumentException("Invalid move: " + notation);
    }

    @Override
    public String toString() {
        return notation;
    }
}
