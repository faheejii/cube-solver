package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LSlot;

public record F2LCaseKey(
        F2LSlot slot,
        F2LCaseSignature signature
) {
    public F2LCaseKey {
        if (slot == null) {
            throw new IllegalArgumentException("slot cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
    }
}
