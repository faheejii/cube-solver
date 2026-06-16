package api;

import cube.Face;
import solver.CfopSolveRequest;
import solver.F2LMode;

public record SolveApiRequest(
        String scramble,
        String crossFace,
        String f2lMode
) {
    public SolveApiRequest(String scramble, String crossFace) {
        this(scramble, crossFace, null);
    }

    public CfopSolveRequest toSolveRequest() {
        var mode = F2LMode.fromApiValue(f2lMode);
        if (crossFace != null && isColorNeutral(crossFace)) {
            return CfopSolveRequest.colorNeutral(scramble, mode);
        }

        var face = (crossFace == null || crossFace.isBlank())
                ? Face.U
                : Face.fromNotation(crossFace.trim().charAt(0));
        return new CfopSolveRequest(scramble, face, mode);
    }

    private static boolean isColorNeutral(String value) {
        var normalized = value.trim()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "")
                .toUpperCase();
        return normalized.equals("CN") || normalized.equals("COLORNEUTRAL");
    }
}
