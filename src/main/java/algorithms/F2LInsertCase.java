package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Algorithm;

public record F2LInsertCase(
        F2LSlot insertSlot,
        F2LPreservationMask preservedSlots,
        F2LCaseSignature signature,
        Algorithm algorithm,
        String name
) {
    public F2LInsertCase {
        if (insertSlot == null) {
            throw new IllegalArgumentException("insertSlot cannot be null");
        }
        if (preservedSlots == null) {
            throw new IllegalArgumentException("preservedSlots cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }
    }

    public F2LInsertCaseKey key() {
        return new F2LInsertCaseKey(insertSlot, preservedSlots, signature);
    }
}
