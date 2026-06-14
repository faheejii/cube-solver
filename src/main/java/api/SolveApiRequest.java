package api;

import cube.Face;
import solver.CfopSolveRequest;

public record SolveApiRequest(
        String scramble,
        String crossFace
) {
    public CfopSolveRequest toSolveRequest() {
        var face = (crossFace == null || crossFace.isBlank())
                ? Face.U
                : Face.fromNotation(crossFace.trim().charAt(0));
        return new CfopSolveRequest(scramble, face);
    }
}
