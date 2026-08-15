package cfop;

import cube.Corner;
import cube.Edge;

/**
 * Identity of a setup-stage pair state.
 *
 * <p>Unlike an insert signature this deliberately has no target-slot field.
 * Setup algorithms prepare a pair and preserve already solved slots; the
 * eventual insert target is selected only after the setup has been applied.</p>
 */
public record F2LSetupSignature(
        Corner cornerPosition,
        int cornerOrientation,
        Edge edgePosition,
        int edgeOrientation
) {
    public F2LSetupSignature {
        if (cornerPosition == null || edgePosition == null) {
            throw new IllegalArgumentException("setup signature pieces cannot be null");
        }
        if (cornerOrientation < 0 || cornerOrientation > 2) {
            throw new IllegalArgumentException("Corner orientation must be 0..2");
        }
        if (edgeOrientation < 0 || edgeOrientation > 1) {
            throw new IllegalArgumentException("Edge orientation must be 0..1");
        }
    }

    public static F2LSetupSignature from(F2LCaseSignature signature) {
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
        return new F2LSetupSignature(
                signature.cornerPosition(), signature.cornerOrientation(),
                signature.edgePosition(), signature.edgeOrientation()
        );
    }
}
