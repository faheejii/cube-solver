package solver;

import cube.Face;

public record CfopSolveRequest(
        String scramble,
        Face crossFace
) {
    public CfopSolveRequest {
        if (scramble == null || scramble.isBlank()) {
            throw new IllegalArgumentException("scramble cannot be null or blank");
        }
        if (crossFace == null) {
            throw new IllegalArgumentException("crossFace cannot be null");
        }
    }
}
