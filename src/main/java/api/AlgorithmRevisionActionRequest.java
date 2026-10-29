package api;

public record AlgorithmRevisionActionRequest(String action, Long expectedVersion, String reason) { }
