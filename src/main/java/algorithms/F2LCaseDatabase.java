package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.CubeState;
import cube.Move;
import cube.MoveApplier;
import util.NotationNormalizer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class F2LCaseDatabase {
    private final Map<F2LCaseSignature, F2LCase> cases = new LinkedHashMap<>();

    public static F2LCaseDatabase empty() {
        return new F2LCaseDatabase();
    }

    public static F2LCaseDatabase seedBasicCases() {
        var database = new F2LCaseDatabase();

        database.register("R U' R'", "R U R'", "basic-insert-1");
        database.register("R U R'", "R U' R'", "basic-insert-2");
        database.register("R' F R F2 U2 F", "F' U2 F U' F' U F", "basic-insert-3");
        database.register("F' U F", "F' U' F", "basic-insert-4");
        database.register("F' U' F", "F' U F", "basic-insert-5");
        database.register("F' U F U' R U R'", "R U' R' U F' U' F", "basic-insert-6");
        database.register("F' U F U' R U2 R'", "R U2 R' U F' U' F", "basic-insert-7");
        database.register("F' U F U F' U' F", "F' U F U' F' U' F", "basic-insert-8");
        database.register("R U R' U2 R U' R'", "R U R' U2 R U' R'", "basic-insert-9");
        database.register("R U R' U2 R U2 R'", "R U2 R' U2 R U' R'", "basic-insert-10");
        database.register("R U R' U' R U' R'", "R U R' U R U' R'", "basic-insert-11");

        return database;
    }

    private static boolean isCubeRotation(Move move) {
        return switch (move) {
            case X, X2, X_PRIME, Y, Y2, Y_PRIME, Z, Z2, Z_PRIME -> true;
            default -> false;
        };
    }

    public void register(String setup, String algorithm, String name) {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, NotationNormalizer.normalizePrimes(setup));
        var signature = F2LCaseSignatureExtractor.extract(cube, F2LSlot.FR);

        var f2lCase = new F2LCase(signature, Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm)), name);

        register(f2lCase);
    }

    public void register(F2LCase f2lCase) {
        if (f2lCase == null) {
            throw new IllegalArgumentException("f2lCase cannot be null");
        }
        if (cases.containsKey(f2lCase.signature())) {
            throw new IllegalArgumentException("Duplicate F2L case signature: " + f2lCase.signature());
        }
        cases.put(f2lCase.signature(), f2lCase);
    }

    public Optional<F2LCase> find(F2LCaseSignature signature) {
        return Optional.ofNullable(cases.get(signature));
    }

    public boolean contains(F2LCaseSignature signature) {
        return cases.containsKey(signature);
    }

    public int size() {
        return cases.size();
    }

    public Collection<F2LCase> allCases() {
        return cases.values();
    }

    public void validate() {
        for (var f2lCase : cases.values()) {
            for (var move : f2lCase.algorithm().getMoves()) {
                if (isCubeRotation(move)) {
                    throw new IllegalArgumentException("F2L DB algorithms must not contain cube rotations: " + f2lCase.name());
                }
            }
        }
    }
}
