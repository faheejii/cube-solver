package cfop;

public record OLLCaseSignature(
        int urfCornerOrientation,
        int uflCornerOrientation,
        int ulbCornerOrientation,
        int ubrCornerOrientation,
        int urEdgeOrientation,
        int ufEdgeOrientation,
        int ulEdgeOrientation,
        int ubEdgeOrientation
) {
    public OLLCaseSignature {
        validateCorner(urfCornerOrientation, "URF");
        validateCorner(uflCornerOrientation, "UFL");
        validateCorner(ulbCornerOrientation, "ULB");
        validateCorner(ubrCornerOrientation, "UBR");
        validateEdge(urEdgeOrientation, "UR");
        validateEdge(ufEdgeOrientation, "UF");
        validateEdge(ulEdgeOrientation, "UL");
        validateEdge(ubEdgeOrientation, "UB");
    }

    private static void validateCorner(int value, String label) {
        if (value < 0 || value > 2) {
            throw new IllegalArgumentException("Corner orientation for " + label + " must be 0..2");
        }
    }

    private static void validateEdge(int value, String label) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("Edge orientation for " + label + " must be 0..1");
        }
    }
}
