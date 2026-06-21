package statistics;

public record RollingAverage(
        String status,
        Integer valueMs
) {
    public static RollingAverage value(int valueMs) {
        return new RollingAverage("value", valueMs);
    }

    public static RollingAverage dnf() {
        return new RollingAverage("dnf", null);
    }

    public static RollingAverage insufficient() {
        return new RollingAverage("insufficient", null);
    }
}
