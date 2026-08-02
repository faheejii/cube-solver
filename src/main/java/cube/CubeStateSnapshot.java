package cube;

import java.util.Arrays;

/**
 * Immutable value snapshot of a cube state for trace and diagnostic data.
 */
public final class CubeStateSnapshot {
    private final byte[] cornerPerm;
    private final byte[] cornerOri;
    private final byte[] edgePerm;
    private final byte[] edgeOri;

    private CubeStateSnapshot(byte[] cornerPerm, byte[] cornerOri, byte[] edgePerm, byte[] edgeOri) {
        this.cornerPerm = copyAndValidate(cornerPerm, 8, "cornerPerm");
        this.cornerOri = copyAndValidate(cornerOri, 8, "cornerOri");
        this.edgePerm = copyAndValidate(edgePerm, 12, "edgePerm");
        this.edgeOri = copyAndValidate(edgeOri, 12, "edgeOri");
    }

    public static CubeStateSnapshot from(CubeState cube) {
        if (cube == null) {
            throw new IllegalArgumentException("cube cannot be null");
        }
        return new CubeStateSnapshot(cube.cornerPerm, cube.cornerOri, cube.edgePerm, cube.edgeOri);
    }

    public CubeState toCubeState() {
        var cube = new CubeState();
        cube.cornerPerm = cornerPerm.clone();
        cube.cornerOri = cornerOri.clone();
        cube.edgePerm = edgePerm.clone();
        cube.edgeOri = edgeOri.clone();
        return cube;
    }

    public byte[] cornerPerm() {
        return cornerPerm.clone();
    }

    public byte[] cornerOri() {
        return cornerOri.clone();
    }

    public byte[] edgePerm() {
        return edgePerm.clone();
    }

    public byte[] edgeOri() {
        return edgeOri.clone();
    }

    private static byte[] copyAndValidate(byte[] values, int expectedLength, String name) {
        if (values == null || values.length != expectedLength) {
            throw new IllegalArgumentException(name + " must contain " + expectedLength + " values");
        }
        return values.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CubeStateSnapshot that)) {
            return false;
        }
        return Arrays.equals(cornerPerm, that.cornerPerm)
                && Arrays.equals(cornerOri, that.cornerOri)
                && Arrays.equals(edgePerm, that.edgePerm)
                && Arrays.equals(edgeOri, that.edgeOri);
    }

    @Override
    public int hashCode() {
        var result = Arrays.hashCode(cornerPerm);
        result = 31 * result + Arrays.hashCode(cornerOri);
        result = 31 * result + Arrays.hashCode(edgePerm);
        return 31 * result + Arrays.hashCode(edgeOri);
    }
}
