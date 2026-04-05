package test;

import cube.CubeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CubeStateTest {

    @Test
    void newCube_shouldBeSolved() {
        CubeState cube = new CubeState();

        for (int i = 0; i < 8; i++) {
            assertEquals(i, cube.cornerPerm[i], "Corner perm mismatch at index " + i);
            assertEquals(0, cube.cornerOri[i], "Corner orientation should be 0 at index " + i);
        }

        for (int i = 0; i < 12; i++) {
            assertEquals(i, cube.edgePerm[i], "Edge perm mismatch at index " + i);
            assertEquals(0, cube.edgeOri[i], "Edge orientation should be 0 at index " + i);
        }
    }
}