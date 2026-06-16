package solver;

public enum F2LMode {
    GREEDY("greedy"),
    OPTIMIZED("optimized");

    private final String apiValue;

    F2LMode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static F2LMode fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return GREEDY;
        }
        var normalized = value.trim().replace("-", "_").toUpperCase();
        return switch (normalized) {
            case "GREEDY", "FAST" -> GREEDY;
            case "OPTIMIZED", "OPTIMISED" -> OPTIMIZED;
            default -> throw new IllegalArgumentException("Invalid F2L mode: " + value);
        };
    }
}
