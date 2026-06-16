package solver;

import cube.Algorithm;
import cube.Face;

public record CrossSolution(
        Face face,
        Algorithm algorithm
) {
    public CrossSolution {
        if (face == null) {
            throw new IllegalArgumentException("face cannot be null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm cannot be null");
        }
    }
}
