package cfop;

import cube.Corner;
import cube.Edge;

public record OLLCaseSignature(
        Corner urfCornerPiece,
        int urfCornerOrientation,
        Corner uflCornerPiece,
        int uflCornerOrientation,
        Corner ulbCornerPiece,
        int ulbCornerOrientation,
        Corner ubrCornerPiece,
        int ubrCornerOrientation,
        Edge urEdgePiece,
        int urEdgeOrientation,
        Edge ufEdgePiece,
        int ufEdgeOrientation,
        Edge ulEdgePiece,
        int ulEdgeOrientation,
        Edge ubEdgePiece,
        int ubEdgeOrientation
) {
    public OLLCaseSignature {
        requireCorner(urfCornerPiece, "URF");
        validateCorner(urfCornerOrientation, "URF");
        requireCorner(uflCornerPiece, "UFL");
        validateCorner(uflCornerOrientation, "UFL");
        requireCorner(ulbCornerPiece, "ULB");
        validateCorner(ulbCornerOrientation, "ULB");
        requireCorner(ubrCornerPiece, "UBR");
        validateCorner(ubrCornerOrientation, "UBR");

        requireEdge(urEdgePiece, "UR");
        validateEdge(urEdgeOrientation, "UR");
        requireEdge(ufEdgePiece, "UF");
        validateEdge(ufEdgeOrientation, "UF");
        requireEdge(ulEdgePiece, "UL");
        validateEdge(ulEdgeOrientation, "UL");
        requireEdge(ubEdgePiece, "UB");
        validateEdge(ubEdgeOrientation, "UB");
    }

    private static void requireCorner(Corner piece, String label) {
        if (piece == null) {
            throw new IllegalArgumentException("Corner piece for " + label + " cannot be null");
        }
    }

    private static void requireEdge(Edge piece, String label) {
        if (piece == null) {
            throw new IllegalArgumentException("Edge piece for " + label + " cannot be null");
        }
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
