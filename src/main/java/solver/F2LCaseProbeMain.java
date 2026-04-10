package solver;

import cfop.F2LCaseSignatureExtractor;
import cfop.F2LSlot;
import cube.OrientedCube;
import util.NotationNormalizer;

public class F2LCaseProbeMain {
    public static void main(String[] args) {
        var parsed = parseArgs(args);
        var setup = parsed.setup();
        var slot = parsed.slot();

        var orientedCube = new OrientedCube();
        if (!setup.isBlank()) {
            orientedCube.applyAlgorithm(NotationNormalizer.normalizePrimes(setup));
        }

        var signature = F2LCaseSignatureExtractor.extract(orientedCube.cubeState(), slot, orientedCube.orientation());

        System.out.println("Setup: " + (setup.isBlank() ? "<solved>" : setup));
        System.out.println("Slot: " + slot);
        System.out.println("Orientation: " + orientedCube.orientation());
        System.out.println("Signature: " + signature);
        System.out.println("DB register:");
        System.out.println("database.register(\"" + setup + "\", \"<solve alg>\", \"case-name\");");
    }

    private static ParsedArgs parseArgs(String[] args) {
        if (args.length == 0) {
            return new ParsedArgs("", F2LSlot.FR);
        }

        var slot = tryParseSlot(args[args.length - 1]);
        if (slot != null) {
            return new ParsedArgs(joinSetup(args, args.length - 1), slot);
        }

        return new ParsedArgs(String.join(" ", args), F2LSlot.FR);
    }

    private static F2LSlot tryParseSlot(String value) {
        try {
            return F2LSlot.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String joinSetup(String[] args, int endExclusive) {
        if (endExclusive <= 0) {
            return "";
        }
        var builder = new StringBuilder();
        for (int i = 0; i < endExclusive; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private record ParsedArgs(String setup, F2LSlot slot) {
    }
}
