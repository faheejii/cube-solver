package algorithms;

import cfop.F2LSetupSignature;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Algorithm;

public record F2LSetupCase(
        F2LPreservationMask preservedSlots,
        F2LSlot nonPreservedSlot,
        F2LSetupSignature signature,
        Algorithm algorithm,
        Algorithm sourceSetup,
        String name
) {
    public F2LSetupCase {
        if (preservedSlots == null) {
            throw new IllegalArgumentException("preservedSlots cannot be null");
        }
        if (nonPreservedSlot == null) {
            throw new IllegalArgumentException("nonPreservedSlot cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm cannot be null");
        }
        if (sourceSetup == null) {
            throw new IllegalArgumentException("sourceSetup cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
    }

    public F2LSetupCaseKey key() {
        return new F2LSetupCaseKey(preservedSlots, signature);
    }
}
