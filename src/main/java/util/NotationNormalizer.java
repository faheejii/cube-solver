package util;

public final class NotationNormalizer {
    private NotationNormalizer() {
    }

    public static String normalizePrimes(String notation) {
        if (notation == null || notation.isBlank()) {
            return notation == null ? null : notation.trim();
        }
        return notation.replace("_PRIME", "'")
                .replace("_prime", "'")
                .trim();
    }
}
