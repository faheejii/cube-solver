package api;

/** Client-authored definition fields; identity, owner, author, and status are server-assigned. */
public record AlgorithmDefinitionRequest(
        String phase,
        String name,
        String algorithm,
        String notes,
        String slot,
        String nonPreservedSlot,
        String sourceSetup
) { }
