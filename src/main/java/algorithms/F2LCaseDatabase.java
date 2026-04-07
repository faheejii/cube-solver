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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class F2LCaseDatabase {
    private final Map<F2LCaseSignature, F2LCase> cases = new LinkedHashMap<>();

    public static F2LCaseDatabase empty() {
        return new F2LCaseDatabase();
    }

    public static F2LCaseDatabase seedBasicCases() {
        return normalizeBySignatureKeepingFirst(seedBasicCaseList());
    }

    public static Map<F2LCaseSignature, List<F2LCase>> duplicateSeedBasicCases() {
        return findDuplicateSignatures(seedBasicCaseList());
    }

    public static F2LCaseDatabase normalizeBySignatureKeepingFirst(Collection<F2LCase> sourceCases) {
        var database = new F2LCaseDatabase();
        for (var f2lCase : sourceCases) {
            if (f2lCase == null) {
                continue;
            }
            database.cases.putIfAbsent(f2lCase.signature(), f2lCase);
        }
        return database;
    }

    public static Map<F2LCaseSignature, List<F2LCase>> findDuplicateSignatures(Collection<F2LCase> sourceCases) {
        var grouped = new LinkedHashMap<F2LCaseSignature, List<F2LCase>>();
        for (var f2lCase : sourceCases) {
            if (f2lCase == null) {
                continue;
            }
            grouped.computeIfAbsent(f2lCase.signature(), ignored -> new ArrayList<>()).add(f2lCase);
        }

        var duplicates = new LinkedHashMap<F2LCaseSignature, List<F2LCase>>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(duplicates);
    }

    private static List<F2LCase> seedBasicCaseList() {
        var cases = new ArrayList<F2LCase>();
        var frSlot = F2LSlot.FR;

        // FR SLOT
        cases.add(caseFromAlgorithm("R U R'", frSlot, "case-1"));
        cases.add(caseFromAlgorithm("R U' R'", frSlot, "case-2"));
        cases.add(caseFromAlgorithm("F' U2 F U' F' U F", frSlot, "case-3"));
        cases.add(caseFromAlgorithm("F' U' F", frSlot, "case-4"));
        cases.add(caseFromAlgorithm("F' U F", frSlot, "case-5"));
        cases.add(caseFromAlgorithm("R U' R' U F' U' F", frSlot, "case-6"));
        cases.add(caseFromAlgorithm("R U2 R' U F' U' F", frSlot, "case-7"));
        cases.add(caseFromAlgorithm("F' U F U' F' U' F", frSlot, "case-8"));
        cases.add(caseFromAlgorithm("R U R' U2 R U' R'", frSlot, "case-9"));
        cases.add(caseFromAlgorithm("R U2 R' U2 R U' R'", frSlot, "case-10"));
        cases.add(caseFromAlgorithm("R U R' U R U' R'", frSlot, "case-11"));
        cases.add(caseFromAlgorithm("R U2 R' U R U' R'", frSlot, "case-12"));
        cases.add(caseFromAlgorithm("F' U L' U L U2 F", frSlot, "case-13"));
        cases.add(caseFromAlgorithm("R U2 R' U' R U R'", frSlot, "case-14"));
        cases.add(caseFromAlgorithm("U R U' R' U' R U' R' U R U' R'", frSlot, "case-15"));
        cases.add(caseFromAlgorithm("U' F' R U R' U' R' F R", frSlot, "case-16"));
        cases.add(caseFromAlgorithm("R U' R' U R U' R'", frSlot, "case-17"));
        cases.add(caseFromAlgorithm("R' F R F' U R U' R'", frSlot, "case-18"));
        cases.add(caseFromAlgorithm("R U' R' U F' U F", frSlot, "case-19"));
        cases.add(caseFromAlgorithm("U' R U' R' U2 R U' R'", frSlot, "case-20"));
        cases.add(caseFromAlgorithm("R U R' U' F' U F", frSlot, "case-21"));
        cases.add(caseFromAlgorithm("R U' R' U' R U R' U2 R U' R'", frSlot, "case-22"));
        cases.add(caseFromAlgorithm("F' U F U2 R U R' U R U' R'", frSlot, "case-23"));

        //FR SLOT NEXT SIDE

        cases.add(caseFromAlgorithm("R U R' U R U R'", frSlot, "case-24"));
        cases.add(caseFromAlgorithm("R' U2 R2 U R2 U R", frSlot, "case-25"));
        cases.add(caseFromAlgorithm("U' R U' R' U R U R'", frSlot, "case-26"));
        cases.add(caseFromAlgorithm("y' U R' U' R U2 R' U R", frSlot, "case-27"));
        cases.add(caseFromAlgorithm("y' U R' U2 R U2 R' U R", frSlot, "case-28"));
        cases.add(caseFromAlgorithm("y' U2 R' U' R U' R' U R", frSlot, "case-29"));
        cases.add(caseFromAlgorithm("R U' R' U y' R' U' R", frSlot, "case-30"));
        cases.add(caseFromAlgorithm("y' R' U2 R U R' U' R", frSlot, "case-31"));

        return List.copyOf(cases);
    }

    public F2LCaseDatabase normalizedBySignatureKeepingFirst() {
        return normalizeBySignatureKeepingFirst(cases.values());
    }

    private static boolean isCubeRotation(Move move) {
        return switch (move) {
            case X, X2, X_PRIME, Z, Z2, Z_PRIME -> true;
            default -> false;
        };
    }

    private static F2LCase caseFromAlgorithm(String algorithm, F2LSlot slot, String name) {
        var cube = new CubeState();
        var alg = Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm));
        var setup = alg.inverse().toString();
        MoveApplier.applyAlgorithm(cube, NotationNormalizer.normalizePrimes(setup));
        var signature = F2LCaseSignatureExtractor.extract(cube, slot);
        return new F2LCase(signature, alg, name);
    }

    private static F2LCase caseFromSetup(String setup, F2LSlot slot, String algorithm, String name) {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, NotationNormalizer.normalizePrimes(setup));
        var signature = F2LCaseSignatureExtractor.extract(cube, slot);
        return new F2LCase(signature, Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm)), name);
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
