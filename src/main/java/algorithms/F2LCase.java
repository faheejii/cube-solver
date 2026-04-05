package algorithms;

import cfop.F2LCaseSignature;
import cube.Algorithm;

public record F2LCase(
        F2LCaseSignature signature,
        Algorithm algorithm,
        String name
) {
    public F2LCase {
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
}
