package test;

import cube.CubeOrientationKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CubeOrientationKeyTest {
    @Test
    void all_shouldEnumerateTwentyFourDistinctOrientations() {
        var orientations = CubeOrientationKey.all();

        assertEquals(24, orientations.size());
        assertEquals(24, orientations.stream().distinct().count());
        for (var orientationKey : orientations) {
            assertEquals(orientationKey, CubeOrientationKey.from(orientationKey.toOrientation()));
        }
    }
}
