package cfop;

import cube.Corner;
import cube.Edge;

/**
 * Canonical F2L case identifier for a single corner-edge pair.
 * Cases are intended to be normalized into the FR working slot before lookup.
 */
public record F2LCaseSignature(
        Corner cornerPosition,
        int cornerOrientation,
        Edge edgePosition,
        int edgeOrientation
) {
    public F2LCaseSignature {
        if (cornerOrientation < 0 || cornerOrientation > 2) {
            throw new IllegalArgumentException("Corner orientation must be 0..2");
        }
        if (edgeOrientation < 0 || edgeOrientation > 1) {
            throw new IllegalArgumentException("Edge orientation must be 0..1");
        }
    }
}
