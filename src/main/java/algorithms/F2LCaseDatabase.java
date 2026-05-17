package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.Move;
import cube.OrientedCube;
import util.NotationNormalizer;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class F2LCaseDatabase {
    private static final boolean DEBUG_DB = Boolean.getBoolean("f2l.debug");
    private final Map<F2LCaseKey, F2LCase> cases = new LinkedHashMap<>();

    public static F2LCaseDatabase empty() {
        return new F2LCaseDatabase();
    }

    public static F2LCaseDatabase seedBasicCases() {
        return normalizeBySignatureKeepingFirst(seedBasicCaseList());
    }

    public static Map<F2LCaseKey, List<F2LCase>> duplicateSeedBasicCases() {
        return findDuplicateSignatures(seedBasicCaseList());
    }

    public static F2LCaseDatabase normalizeBySignatureKeepingFirst(Collection<F2LCase> sourceCases) {
        var database = new F2LCaseDatabase();
        for (var f2lCase : sourceCases) {
            if (f2lCase == null) {
                continue;
            }
            database.cases.putIfAbsent(f2lCase.key(), f2lCase);
        }
        return database;
    }

    public static Map<F2LCaseKey, List<F2LCase>> findDuplicateSignatures(Collection<F2LCase> sourceCases) {
        var grouped = new LinkedHashMap<F2LCaseKey, List<F2LCase>>();
        for (var f2lCase : sourceCases) {
            if (f2lCase == null) {
                continue;
            }
            grouped.computeIfAbsent(f2lCase.key(), ignored -> new ArrayList<>()).add(f2lCase);
        }

        var duplicates = new LinkedHashMap<F2LCaseKey, List<F2LCase>>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(duplicates);
    }

    private static List<F2LCase> seedBasicCaseList() {
        var cases = new SeedCaseList();
        var fr = F2LSlot.FR;
        var br = F2LSlot.BR;
        var fl = F2LSlot.FL;
        var bl = F2LSlot.BL;

        // free pairs
        cases.add("R U' R'", fr);                         // case-1
        cases.add("F' r U r'", fl);                       // case-2
        cases.add("L U' L'", bl);                         // case-3

        cases.add("F R' F' R", fr);                       // case-4
        cases.add("L' U L", fl);                          // case-5
        cases.add("R' U R", br);                          // case-6

        cases.add("F' U' F", fr);                         // case-7
        cases.add("L' U' L", fl);                         // case-8
        cases.add("y R' U' R", bl);                       // case-9
        cases.add("R' U' R", br);                         // case-10

        cases.add("R U R'", fr);                          // case-11
        cases.add("F U F'", fl);                          // case-12
        cases.add("L U L'", bl);                          // case-13

        // others
        cases.add("R U R' U2 R U' R'", fr);               // case-14
        cases.add("R' U' R U2 R' U R", br);               // case-15
        cases.add("R U2 R' U' R U2 R'", fr);              // case-16
        cases.add("R' U2 R U R' U2 R", br);               // case-17
        cases.add("L' U' L U' L' U' L", fl);              // case-18
        cases.add("R U R' U R U R'", fr);                 // case-19
        cases.add("R U2 R' U F' U' F", fr);               // case-20
        cases.add("R' U2 R2 U R2 U R", fr);               // case-21
        cases.add("R' U R U' R' U' R", br);               // case-22
        cases.add("R U' R' U R U R'", fr);                // case-23
        cases.add("R' D' R U' R' D R U R U' R'", fr);     // case-24
        cases.add("R U' R' U2 F' U' F", fr);              // case-25
        cases.add("R U2 R' U' R U R'", fr);               // case-26
        cases.add("L' U2 L U L' U' L", fl);               // case-27
        cases.add("U R' F' U2 F R U R' U' R", br);        // case-28
        cases.add("L' U2 L U' L' U L", fl);               // case-29
        cases.add("L U' L' U2 L U L'", bl);               // case-30
        cases.add("R' U R U2 R' U' R", br);               // case-31
        cases.add("R U' R' U' R U' R' U R U' R'", fr);    // case-32
        cases.add("L' U L U L' U L U' L' U L", fl);       // case-33
        cases.add("F' R U R' U' R' F R", fr);             // case-34
        cases.add("R U' R' F R' F' R", fr);               // case-35
        cases.add("R U' R' U R U' R'", fr);               // case-36
        cases.add("L' U L U' L' U L", fl);                // case-37
        cases.add("R' U' R U R' U' R", br);               // case-38
        cases.add("R U R' U' R U R'", fr);                // case-39
        cases.add("R' F R F' R U' R'", fr);               // case-40
        cases.add("R U' R' U R U' R' U R U' R'", fr);     // case-41
        cases.add("R U' R' U2 R U' R'", fr);              // case-42
        cases.add("L' U L U2 L' U L", fl);                // case-43
        cases.add("R U R' F R' F' R", fr);                // case-44
        cases.add("L' U' L F' L F L'", fl);               // case-45
        cases.add("R2 U2 F R2 F' U2 R' U R'", fr);        // case-46
        cases.add("R U' R' U' R U R' U2 R U' R'", fr);    // case-47
        cases.add("R U R' U2 R U' R' U R U R'", fr);      // case-48
        cases.add("R U' R' U' R U' R' U y' R' U' R", fr); // case-49
        cases.add("R U' R' F' L' U2 L F", fr);            // case-50
        return cases.toList();
    }

    private static boolean isUnsupportedRotation(Move move) {
        return switch (move) {
            case X, X2, X_PRIME, Z, Z2, Z_PRIME -> true;
            default -> false;
        };
    }

    private static F2LCase caseFromAlgorithm(String algorithm, F2LSlot slot, String name) {
        var alg = Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm));
        var setup = alg.inverse().toString();
        var orientedCube = new OrientedCube();
        orientedCube.applyAlgorithm(NotationNormalizer.normalizePrimes(setup));
        var signature = F2LCaseSignatureExtractor.extract(orientedCube.cubeState(), slot, orientedCube.orientation());
        var f2lCase = new F2LCase(slot, signature, alg, name);
        debugRegisteredCase(f2lCase, setup);
        return f2lCase;
    }

    private static F2LCase caseFromSetup(String setup, F2LSlot slot, String algorithm, String name) {
        var orientedCube = new OrientedCube();
        orientedCube.applyAlgorithm(NotationNormalizer.normalizePrimes(setup));
        var signature = F2LCaseSignatureExtractor.extract(orientedCube.cubeState(), slot, orientedCube.orientation());
        var f2lCase = new F2LCase(slot, signature, Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm)), name);
        debugRegisteredCase(f2lCase, setup);
        return f2lCase;
    }

    private static void debugRegisteredCase(F2LCase f2lCase, String setup) {
        if (!DEBUG_DB) {
            return;
        }
        System.out.println("[F2L DB CASE] name=" + f2lCase.name()
                + " slot=" + f2lCase.slot()
                + " signature=" + f2lCase.signature()
                + " algorithm=" + f2lCase.algorithm()
                + " setup=" + setup);
    }

    public F2LCaseDatabase normalizedBySignatureKeepingFirst() {
        return normalizeBySignatureKeepingFirst(cases.values());
    }

    public void register(String algorithm, F2LSlot slot, String name) {
        register(caseFromAlgorithm(algorithm, slot, name));
    }

    public void register(String setup, F2LSlot slot, String algorithm, String name) {
        register(caseFromSetup(setup, slot, algorithm, name));
    }

    public void register(F2LCase f2lCase) {
        if (f2lCase == null) {
            throw new IllegalArgumentException("f2lCase cannot be null");
        }
        if (cases.containsKey(f2lCase.key())) {
            throw new IllegalArgumentException("Duplicate F2L case key: " + f2lCase.key());
        }
        cases.put(f2lCase.key(), f2lCase);
    }

    public Optional<F2LCase> find(F2LSlot slot, F2LCaseSignature signature) {
        return Optional.ofNullable(cases.get(new F2LCaseKey(slot, signature)));
    }

    public Optional<F2LCase> find(F2LCaseKey key) {
        return Optional.ofNullable(cases.get(key));
    }

    public boolean contains(F2LSlot slot, F2LCaseSignature signature) {
        return cases.containsKey(new F2LCaseKey(slot, signature));
    }

    public boolean contains(F2LCaseKey key) {
        return cases.containsKey(key);
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
                if (isUnsupportedRotation(move)) {
                    throw new IllegalArgumentException("F2L DB algorithms must not contain cube rotations: " + f2lCase.name());
                }
            }
        }
    }

    private static final class SeedCaseList {
        private final List<F2LCase> cases = new ArrayList<>();
        private int nextCaseNumber = 1;

        private void add(String algorithm, F2LSlot slot) {
            cases.add(caseFromAlgorithm(algorithm, slot, "case-" + nextCaseNumber++));
        }

        private List<F2LCase> toList() {
            return List.copyOf(cases);
        }
    }
}
