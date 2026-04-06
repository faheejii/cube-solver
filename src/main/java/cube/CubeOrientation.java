package cube;

public class CubeOrientation {
    private Vector right = new Vector(1, 0, 0);
    private Vector up = new Vector(0, 1, 0);
    private Vector front = new Vector(0, 0, 1);

    public CubeOrientation() {
    }

    public CubeOrientation copy() {
        var copy = new CubeOrientation();
        copy.right = right;
        copy.up = up;
        copy.front = front;
        return copy;
    }

    public Face faceAt(Face logicalFace) {
        return switch (logicalFace) {
            case U -> faceFromVector(up);
            case R -> faceFromVector(right);
            case F -> faceFromVector(front);
            case D -> faceFromVector(up.negate());
            case L -> faceFromVector(right.negate());
            case B -> faceFromVector(front.negate());
        };
    }

    public Face logicalFaceOf(Face physicalFace) {
        for (var logical : Face.values()) {
            if (faceAt(logical) == physicalFace) {
                return logical;
            }
        }
        throw new IllegalStateException("Unexpected physical face: " + physicalFace);
    }

    public Move mapMove(Move move) {
        var amount = amountOf(move);
        return switch (baseOf(move)) {
            case U, R, F, D, L, B -> faceMove(faceFromVector(transform(faceVector(baseOf(move)))), amount);
            case M, E, S -> mapSliceMove(baseOf(move), amount);
            default -> throw new IllegalArgumentException("Cannot map cube rotation through orientation: " + move);
        };
    }

    public void applyRotation(Move move) {
        switch (move) {
            case X -> rotateX();
            case X2 -> {
                rotateX();
                rotateX();
            }
            case X_PRIME -> {
                rotateX();
                rotateX();
                rotateX();
            }
            case Y -> rotateY();
            case Y2 -> {
                rotateY();
                rotateY();
            }
            case Y_PRIME -> {
                rotateY();
                rotateY();
                rotateY();
            }
            case Z -> rotateZ();
            case Z2 -> {
                rotateZ();
                rotateZ();
            }
            case Z_PRIME -> {
                rotateZ();
                rotateZ();
                rotateZ();
            }
            default -> throw new IllegalArgumentException("Not a cube rotation: " + move);
        }
    }

    private Move mapSliceMove(Move base, int amount) {
        var vector = transform(sliceVector(base));

        if (vector.equals(new Vector(-1, 0, 0))) {
            return sliceMove(Move.M, amount);
        }
        if (vector.equals(new Vector(1, 0, 0))) {
            return sliceMove(Move.M, invertAmount(amount));
        }
        if (vector.equals(new Vector(0, -1, 0))) {
            return sliceMove(Move.E, amount);
        }
        if (vector.equals(new Vector(0, 1, 0))) {
            return sliceMove(Move.E, invertAmount(amount));
        }
        if (vector.equals(new Vector(0, 0, 1))) {
            return sliceMove(Move.S, amount);
        }
        if (vector.equals(new Vector(0, 0, -1))) {
            return sliceMove(Move.S, invertAmount(amount));
        }

        throw new IllegalStateException("Unexpected slice vector: " + vector);
    }

    private void rotateX() {
        var newRight = right;
        var newUp = front;
        var newFront = up.negate();
        right = newRight;
        up = newUp;
        front = newFront;
    }

    private void rotateY() {
        var newRight = front.negate();
        var newUp = up;
        var newFront = right;
        right = newRight;
        up = newUp;
        front = newFront;
    }

    private void rotateZ() {
        var newRight = up;
        var newUp = right.negate();
        var newFront = front;
        right = newRight;
        up = newUp;
        front = newFront;
    }

    private Vector transform(Vector vector) {
        return right.scale(vector.x())
                .add(up.scale(vector.y()))
                .add(front.scale(vector.z()));
    }

    private static Move baseOf(Move move) {
        return switch (move) {
            case U, U2, U_PRIME -> Move.U;
            case R, R2, R_PRIME -> Move.R;
            case F, F2, F_PRIME -> Move.F;
            case D, D2, D_PRIME -> Move.D;
            case L, L2, L_PRIME -> Move.L;
            case B, B2, B_PRIME -> Move.B;
            case M, M2, M_PRIME -> Move.M;
            case E, E2, E_PRIME -> Move.E;
            case S, S2, S_PRIME -> Move.S;
            default -> move;
        };
    }

    private static int amountOf(Move move) {
        return switch (move) {
            case U, R, F, D, L, B, M, E, S -> 1;
            case U2, R2, F2, D2, L2, B2, M2, E2, S2 -> 2;
            case U_PRIME, R_PRIME, F_PRIME, D_PRIME, L_PRIME, B_PRIME, M_PRIME, E_PRIME, S_PRIME -> 3;
            default -> throw new IllegalArgumentException("Unsupported move amount for: " + move);
        };
    }

    private static int invertAmount(int amount) {
        return switch (amount) {
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 1;
            default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
        };
    }

    private static Vector faceVector(Move move) {
        return switch (move) {
            case U -> new Vector(0, 1, 0);
            case R -> new Vector(1, 0, 0);
            case F -> new Vector(0, 0, 1);
            case D -> new Vector(0, -1, 0);
            case L -> new Vector(-1, 0, 0);
            case B -> new Vector(0, 0, -1);
            default -> throw new IllegalArgumentException("Not a face move: " + move);
        };
    }

    private static Vector sliceVector(Move move) {
        return switch (move) {
            case M -> new Vector(-1, 0, 0);
            case E -> new Vector(0, -1, 0);
            case S -> new Vector(0, 0, 1);
            default -> throw new IllegalArgumentException("Not a slice move: " + move);
        };
    }

    private static Face faceFromVector(Vector vector) {
        if (vector.equals(new Vector(0, 1, 0))) {
            return Face.U;
        }
        if (vector.equals(new Vector(1, 0, 0))) {
            return Face.R;
        }
        if (vector.equals(new Vector(0, 0, 1))) {
            return Face.F;
        }
        if (vector.equals(new Vector(0, -1, 0))) {
            return Face.D;
        }
        if (vector.equals(new Vector(-1, 0, 0))) {
            return Face.L;
        }
        if (vector.equals(new Vector(0, 0, -1))) {
            return Face.B;
        }
        throw new IllegalStateException("Unexpected face vector: " + vector);
    }

    private static Move faceMove(Face face, int amount) {
        return switch (face) {
            case U -> switch (amount) {
                case 1 -> Move.U;
                case 2 -> Move.U2;
                case 3 -> Move.U_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            case R -> switch (amount) {
                case 1 -> Move.R;
                case 2 -> Move.R2;
                case 3 -> Move.R_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            case F -> switch (amount) {
                case 1 -> Move.F;
                case 2 -> Move.F2;
                case 3 -> Move.F_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            case D -> switch (amount) {
                case 1 -> Move.D;
                case 2 -> Move.D2;
                case 3 -> Move.D_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            case L -> switch (amount) {
                case 1 -> Move.L;
                case 2 -> Move.L2;
                case 3 -> Move.L_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            case B -> switch (amount) {
                case 1 -> Move.B;
                case 2 -> Move.B2;
                case 3 -> Move.B_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
        };
    }

    private static Move sliceMove(Move base, int amount) {
        return switch (base) {
            case M -> switch (amount) {
                case 1 -> Move.M;
                case 2 -> Move.M2;
                case 3 -> Move.M_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            case E -> switch (amount) {
                case 1 -> Move.E;
                case 2 -> Move.E2;
                case 3 -> Move.E_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            case S -> switch (amount) {
                case 1 -> Move.S;
                case 2 -> Move.S2;
                case 3 -> Move.S_PRIME;
                default -> throw new IllegalArgumentException("Unsupported move amount: " + amount);
            };
            default -> throw new IllegalArgumentException("Not a slice move family: " + base);
        };
    }

    private record Vector(int x, int y, int z) {
        private Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        private Vector scale(int factor) {
            return new Vector(x * factor, y * factor, z * factor);
        }

        private Vector negate() {
            return new Vector(-x, -y, -z);
        }
    }
}
