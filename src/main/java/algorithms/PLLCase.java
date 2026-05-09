package algorithms;

import cfop.PLLCaseSignature;
import cube.Algorithm;

public record PLLCase(
        PLLCaseSignature signature,
        Algorithm algorithm,
        String name
) {
    public PLLCase {
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
