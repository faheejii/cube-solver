package solver;

/** Per-request preference controlling whether cross searches may use B turns. */
public record CrossSearchOptions(boolean avoidBTurns) {
    public static CrossSearchOptions defaultsFor(F2LMode mode) {
        return new CrossSearchOptions(mode != F2LMode.OPTIMIZED);
    }
}
