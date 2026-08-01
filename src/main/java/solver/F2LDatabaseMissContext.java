package solver;

import cfop.F2LCaseSignature;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;

import java.util.Objects;

public record F2LDatabaseMissContext(
        Phase phase,
        F2LSlot insertSlot,
        F2LPreservationMask requiredPreservation,
        F2LCaseSignature signature,
        int setupCaseCount,
        int insertCaseCount
) {
    public F2LDatabaseMissContext {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(insertSlot, "insertSlot");
        Objects.requireNonNull(requiredPreservation, "requiredPreservation");
        Objects.requireNonNull(signature, "signature");
        if (setupCaseCount < 0 || insertCaseCount < 0) {
            throw new IllegalArgumentException("F2L case counts cannot be negative");
        }
    }

    public enum Phase {
        SETUP,
        INSERT
    }
}
