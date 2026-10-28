package api;

public record AlgorithmRevisionRequest(AlgorithmDefinitionRequest definition, Long expectedVersion) { }
