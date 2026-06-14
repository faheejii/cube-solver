package util;

import java.util.ArrayList;
import java.util.List;

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

    public static String normalizeLastLayerAlgorithm(String notation) {
        var normalized = normalizePrimes(notation);
        if (normalized == null || normalized.isBlank()) {
            return normalized;
        }

        var expanded = new ArrayList<String>();
        for (var token : normalized.split("\\s+")) {
            expanded.addAll(expandLastLayerToken(token));
        }
        return String.join(" ", expanded);
    }

    private static List<String> expandLastLayerToken(String token) {
        return switch (token) {
            case "r" -> List.of("R", "M'");
            case "r2" -> List.of("R2", "M2");
            case "r'" -> List.of("R'", "M");
            case "l" -> List.of("L", "M");
            case "l2" -> List.of("L2", "M2");
            case "l'" -> List.of("L'", "M'");
            case "u" -> List.of("U", "E");
            case "u2" -> List.of("U2", "E2");
            case "u'" -> List.of("U'", "E'");
            case "d" -> List.of("D", "E'");
            case "d2" -> List.of("D2", "E2");
            case "d'" -> List.of("D'", "E");
            case "f" -> List.of("F", "S");
            case "f2" -> List.of("F2", "S2");
            case "f'" -> List.of("F'", "S'");
            case "b" -> List.of("B", "S'");
            case "b2" -> List.of("B2", "S2");
            case "b'" -> List.of("B'", "S");
            default -> List.of(token);
        };
    }
}
