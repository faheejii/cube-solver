package cube;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CubeOrientationKey(
        Face up,
        Face right,
        Face front
) {
    private static final Map<CubeOrientationKey, CubeOrientation> ORIENTATIONS_BY_KEY = buildOrientationsByKey();
    private static final List<CubeOrientationKey> ALL_KEYS = List.copyOf(ORIENTATIONS_BY_KEY.keySet());

    public CubeOrientationKey {
        if (up == null || right == null || front == null) {
            throw new IllegalArgumentException("orientation faces cannot be null");
        }
        validateFrame(up, right, front);
    }

    public static CubeOrientationKey from(CubeOrientation orientation) {
        if (orientation == null) {
            throw new IllegalArgumentException("orientation cannot be null");
        }
        return new CubeOrientationKey(
                orientation.faceAt(Face.U),
                orientation.faceAt(Face.R),
                orientation.faceAt(Face.F)
        );
    }

    public static List<CubeOrientationKey> all() {
        return ALL_KEYS;
    }

    public CubeOrientation toOrientation() {
        var orientation = ORIENTATIONS_BY_KEY.get(this);
        if (orientation == null) {
            throw new IllegalStateException("Unknown cube orientation key: " + this);
        }
        return orientation.copy();
    }

    private static Map<CubeOrientationKey, CubeOrientation> buildOrientationsByKey() {
        var byKey = new LinkedHashMap<CubeOrientationKey, CubeOrientation>();
        var queue = new ArrayDeque<CubeOrientation>();
        queue.add(new CubeOrientation());

        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            var key = from(current);
            if (byKey.containsKey(key)) {
                continue;
            }
            byKey.put(key, current.copy());
            for (var rotation : List.of(Move.X, Move.Y, Move.Z)) {
                var next = current.copy();
                next.applyRotation(rotation);
                queue.addLast(next);
            }
        }

        if (byKey.size() != 24) {
            throw new IllegalStateException("Expected 24 cube orientations but found " + byKey.size());
        }
        return Map.copyOf(byKey);
    }

    private static void validateFrame(Face up, Face right, Face front) {
        var upVector = vectorFor(up);
        var rightVector = vectorFor(right);
        var frontVector = vectorFor(front);
        if (dot(upVector, rightVector) != 0 || dot(upVector, frontVector) != 0 || dot(rightVector, frontVector) != 0) {
            throw new IllegalArgumentException("Orientation axes must be perpendicular");
        }
        if (!cross(rightVector, upVector).equals(frontVector)) {
            throw new IllegalArgumentException("Orientation axes must form a right-handed frame");
        }
    }

    private static AxisVector vectorFor(Face face) {
        return switch (face) {
            case U -> new AxisVector(0, 1, 0);
            case R -> new AxisVector(1, 0, 0);
            case F -> new AxisVector(0, 0, 1);
            case D -> new AxisVector(0, -1, 0);
            case L -> new AxisVector(-1, 0, 0);
            case B -> new AxisVector(0, 0, -1);
        };
    }

    private static int dot(AxisVector first, AxisVector second) {
        return first.x() * second.x() + first.y() * second.y() + first.z() * second.z();
    }

    private static AxisVector cross(AxisVector first, AxisVector second) {
        return new AxisVector(
                first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x()
        );
    }

    private record AxisVector(int x, int y, int z) {
    }
}
