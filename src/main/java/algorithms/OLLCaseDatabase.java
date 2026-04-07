package algorithms;

import cfop.OLLAnalyzer;
import cfop.OLLCaseSignature;
import cube.Algorithm;
import cube.CubeState;
import cube.Move;
import cube.MoveApplier;
import util.NotationNormalizer;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class OLLCaseDatabase {
    private final Map<OLLCaseSignature, OLLCase> cases = new LinkedHashMap<>();

    public static OLLCaseDatabase empty() {
        return new OLLCaseDatabase();
    }

    public static OLLCaseDatabase seedCases() {
        var database = new OLLCaseDatabase();
        for (var ollCase : normalizeBySignatureKeepingFirst(seedCaseList())) {
            database.register(ollCase);
        }
        return database;
    }

    public static Map<OLLCaseSignature, List<OLLCase>> duplicateSeedCases() {
        return findDuplicateSignatures(seedCaseList());
    }

    private static List<OLLCase> seedCaseList() {
        var ollCases = new ArrayList<OLLCase>();
        var cases = new ArrayList<>(List.of(
                "R U2 R2 F R F' U2 R' F R F'",
                "r U r' U2 r U2 R' U2 R U' r'",
                "r' R2 U R' U r U2 r' U M'",
                "M U' r U2 r' U' R U' R' M'",
                "l' U2 L U L' U l",
                "r U2 R' U' R U' r'",
                "r U R' U R U2 r'",
                "l' U' L U' L' U2 l",
                "R U R' U' R' F R2 U R' U' F'",
                "R U R' U R' F R F' R U2 R'",
                "r U R' U R' F R F' R U2 r'",
                "M' R' U' R U' R' U2 R U' R r'",
                "F U R U' R2 F' R U R U' R'",
                "R' F R U R' F' R F U' F'",
                "l' U' l L' U' L U l' U l",
                "r U r' R U R' U' r U' r'",
                "F R' F' R2 r' U R U' R' U' M'",
                "r U R' U R U2 r2 U' R U' R' U2 r",
                "r' R U R U R' U' M' R' F R F'",
                "r U R' U' M2 U R U' R' U' M'",
                "R U2 R' U' R U R' U' R U' R'",
                "R U2 R2 U' R2 U' R2 U2 R",
                "R2 D' R U2 R' D R U2 R",
                "r U R' U' r' F R F'",
                "F' r U R' U' r' F R",
                "R U2 R' U' R U' R'",
                "R U R' U R U2 R'",
                "r U R' U' r' R U R U' R'",
                "R U R' U' R U' R' F' U' F R U R'",
                "F R' F R2 U' R' U' R U R' F2",
                "R' U' F U R U' R' F' R",
                "L U F' U' L' U L F L'",
                "R U R' U' R' F R F'",
                "R U R2 U' R' F R U R U' F'",
                "R U2 R2 F R F' R U2 R'",
                "L' U' L U' L' U L U L F' L' F",
                "F R' F' R U R U' R'",
                "R U R' U R U' R' U' R' F R F'",
                "L F' L' U' L U F U' L'",
                "R' F R U R' U' F' U R",
                "R U R' U R U2 R' F R U R' U' F'",
                "R' U' R U' R' U2 R F R U R' U' F'",
                "F' U' L' U L F",
                "F U R U' R' F'",
                "F R U R' U' F'",
                "R' U' R' F R F' U R",
                "R' U' R' F R F' R' F R F' U R",
                "F R U R' U' R U R' U' F'",
                "r U' r2 U r2 U r2 U' r",
                "r' U r2 U' r2 U' r2 U r'",
                "F U R U' R' U R U' R' F'",
                "R U R' U R U' B U' B' R'",
                "l' U2 L U L' U' L U L' U l",
                "r U2 R' U' R U R' U' R U' r'",
                "R' F R U R U' R2 F' R2 U' R' U R U R'",
                "r' U' r U' R' U R U' R' U R r' U r",
                "R U R' U' M' U R U' r'"
        ));
        int count = 1;
        for (var ollCase : cases) ollCases.add(caseFromAlgorithm(ollCase, String.format("case-%d", count++)));
        return List.copyOf(ollCases);
    }

    private static boolean isCubeRotation(Move move) {
        return switch (move) {
            case X, X2, X_PRIME, Y, Y2, Y_PRIME, Z, Z2, Z_PRIME -> true;
            default -> false;
        };
    }

    private static OLLCase caseFromAlgorithm(String algorithm, String name) {
        var cube = new CubeState();
        var alg = Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm));
        var setup = alg.inverse().toString();
        MoveApplier.applyAlgorithm(cube, NotationNormalizer.normalizePrimes(setup));
        return new OLLCase(OLLAnalyzer.extractSignature(cube), alg, name);
    }

    private static OLLCase caseFromSetup(String setup, String algorithm, String name) {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, NotationNormalizer.normalizePrimes(setup));
        return new OLLCase(
                OLLAnalyzer.extractSignature(cube),
                Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm)),
                name
        );
    }

    public void register(String algorithm, String name) {
        register(caseFromAlgorithm(algorithm, name));
    }

    public void register(String setup, String algorithm, String name) {
        register(caseFromSetup(setup, algorithm, name));
    }

    public void register(OLLCase ollCase) {
        if (ollCase == null) {
            throw new IllegalArgumentException("ollCase cannot be null");
        }
        if (cases.containsKey(ollCase.signature())) {
            throw new IllegalArgumentException("Duplicate OLL case signature: " + ollCase.signature());
        }
        cases.put(ollCase.signature(), ollCase);
    }

    public Optional<OLLCase> find(OLLCaseSignature signature) {
        return Optional.ofNullable(cases.get(signature));
    }

    public boolean contains(OLLCaseSignature signature) {
        return cases.containsKey(signature);
    }

    public int size() {
        return cases.size();
    }

    public Collection<OLLCase> allCases() {
        return cases.values();
    }

    public OLLCaseDatabase normalizedBySignatureKeepingFirst() {
        var normalized = new OLLCaseDatabase();
        for (var ollCase : normalizeBySignatureKeepingFirst(allCases())) {
            normalized.register(ollCase);
        }
        return normalized;
    }

    public void validate() {
        for (var ollCase : cases.values()) {
            for (var move : ollCase.algorithm().getMoves()) {
                if (isCubeRotation(move)) {
                    throw new IllegalArgumentException("OLL DB algorithms must not contain cube rotations: " + ollCase.name());
                }
            }
        }
    }

    private static List<OLLCase> normalizeBySignatureKeepingFirst(Collection<OLLCase> ollCases) {
        var deduped = new LinkedHashMap<OLLCaseSignature, OLLCase>();
        for (var ollCase : ollCases) {
            deduped.putIfAbsent(ollCase.signature(), ollCase);
        }
        return List.copyOf(deduped.values());
    }

    private static Map<OLLCaseSignature, List<OLLCase>> findDuplicateSignatures(Collection<OLLCase> ollCases) {
        var grouped = new LinkedHashMap<OLLCaseSignature, List<OLLCase>>();
        for (var ollCase : ollCases) {
            if (ollCase == null) {
                continue;
            }
            grouped.computeIfAbsent(ollCase.signature(), ignored -> new ArrayList<>()).add(ollCase);
        }

        var duplicates = new LinkedHashMap<OLLCaseSignature, List<OLLCase>>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(duplicates);
    }
}
