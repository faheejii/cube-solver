package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;

public record F2LSetupCaseKey(
        F2LSlot insertSlot,
        F2LPreservationMask preservedSlots,
        F2LCaseSignature signature
) {
    public F2LSetupCaseKey {
        if (insertSlot == null) {
            throw new IllegalArgumentException("insertSlot cannot be null");
        }
        if (preservedSlots == null) {
            throw new IllegalArgumentException("preservedSlots cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
    }
}
