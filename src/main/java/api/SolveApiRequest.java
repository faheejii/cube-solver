package api;

import cube.Face;
import solver.CfopSolveRequest;
import solver.F2LMode;

public record SolveApiRequest(
        String scramble,
        String crossFace,
        String f2lMode,
        Long deadlineSeconds
) {
    public static final long DEFAULT_DEADLINE_SECONDS = 15L;
    public static final long MIN_DEADLINE_SECONDS = 5L;
    public static final long MAX_DEADLINE_SECONDS = 120L;

    public SolveApiRequest {
        validateDeadlineSeconds(deadlineSeconds);
    }

    public SolveApiRequest(String scramble, String crossFace, String f2lMode) {
        this(scramble, crossFace, f2lMode, null);
    }

    public SolveApiRequest(String scramble, String crossFace) {
        this(scramble, crossFace, null, null);
    }

    public long deadlineSecondsOrDefault() {
        return deadlineSeconds == null ? DEFAULT_DEADLINE_SECONDS : deadlineSeconds;
    }

    public static void validateDeadlineSeconds(Long deadlineSeconds) {
        if (deadlineSeconds != null
                && (deadlineSeconds < MIN_DEADLINE_SECONDS || deadlineSeconds > MAX_DEADLINE_SECONDS)) {
            throw new IllegalArgumentException(
                    "deadlineSeconds must be between " + MIN_DEADLINE_SECONDS + " and " + MAX_DEADLINE_SECONDS
            );
        }
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
