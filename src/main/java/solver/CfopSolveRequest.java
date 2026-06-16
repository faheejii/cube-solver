package solver;

import cube.Face;

public record CfopSolveRequest(
        String scramble,
        Face crossFace,
        boolean colorNeutralCross,
        F2LMode f2lMode
) {
    public CfopSolveRequest(String scramble, Face crossFace) {
        this(scramble, crossFace, false, F2LMode.GREEDY);
    }

    public CfopSolveRequest(String scramble, Face crossFace, F2LMode f2lMode) {
        this(scramble, crossFace, false, f2lMode);
    }

    public static CfopSolveRequest colorNeutral(String scramble) {
        return colorNeutral(scramble, F2LMode.GREEDY);
    }

    public static CfopSolveRequest colorNeutral(String scramble, F2LMode f2lMode) {
        return new CfopSolveRequest(scramble, Face.U, true, f2lMode);
    }

    public CfopSolveRequest {
        if (scramble == null || scramble.isBlank()) {
            throw new IllegalArgumentException("scramble cannot be null or blank");
        }
        if (crossFace == null) {
            throw new IllegalArgumentException("crossFace cannot be null");
        }
        if (f2lMode == null) {
            f2lMode = F2LMode.GREEDY;
        }
    }
}
