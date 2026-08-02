package solver;

import cfop.F2LCaseSignature;

public record F2LCaseDescription(
        F2LCaseSignature signature,
        boolean initiallyConnected,
        boolean cornerInTargetSlot,
        boolean edgeInMiddleLayer
) {
    public F2LCaseDescription {
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
    }
}
