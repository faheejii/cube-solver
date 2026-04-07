package algorithms;

import cfop.OLLCaseSignature;
import cube.Algorithm;

public record OLLCase(
        OLLCaseSignature signature,
        Algorithm algorithm,
        String name
) {
    public OLLCase {
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
