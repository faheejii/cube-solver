package algorithms;

import cfop.F2LPreservationMask;
import cfop.F2LSetupSignature;

public record F2LSetupCaseKey(
        F2LPreservationMask preservedSlots,
        F2LSetupSignature signature
) {
    public F2LSetupCaseKey {
        if (preservedSlots == null) {
            throw new IllegalArgumentException("preservedSlots cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
    }
}
