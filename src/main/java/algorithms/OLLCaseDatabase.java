package algorithms;

import cfop.OLLAnalyzer;
import cfop.OLLCaseSignature;
import cube.Algorithm;
import cube.Move;
import cube.OrientedCube;
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
                "R U2 R2 F R F' U2 R' F R F'", // 1
                "r U r' U2 r U2 R' U2 R U' r'",         // 2
                "r' R2 U R' U r U2 r' U M'",            // 3
                "M U' r U2 r' U' R U' R' M'",           // 4
                "l' U2 L U L' U l",                     // 5
                "r U2 R' U' R U' r'",                   // 6
                "r U R' U R U2 r'",                     // 7
                "l' U' L U' L' U2 l",                   // 8
                "R U R' U' R' F R2 U R' U' F'",         // 9
                "R U R' U R' F R F' R U2 R'",           // 10
                "r U R' U R' F R F' R U2 r'",           // 11
                "M' R' U' R U' R' U2 R U' R r'",        // 12
                "F U R U' R2 F' R U R U' R'",           // 13
                "R' F R U R' F' R F U' F'",             // 14
                "l' U' l L' U' L U l' U l",             // 15
                "r U r' R U R' U' r U' r'",             // 16
                "F R' F' R2 r' U R U' R' U' M'",        // 17
                "r U R' U R U2 r2 U' R U' R' U2 r",     // 18
                "r' R U R U R' U' M' R' F R F'",        // 19
                "r U R' U' M2 U R U' R' U' M'",         // 20
                "R U2 R' U' R U R' U' R U' R'",         // 21
                "R U2 R2 U' R2 U' R2 U2 R",             // 22
                "R2 D' R U2 R' D R U2 R",               // 23
                "r U R' U' r' F R F'",                  // 24
                "F' r U R' U' r' F R",                  // 25
                "R U2 R' U' R U' R'",                   // 26
                "R U R' U R U2 R'",                     // 27
                "r U R' U' r' R U R U' R'",             // 28
                "R U R' U' R U' R' F' U' F R U R'",     // 29
                "F R' F R2 U' R' U' R U R' F2",         // 30
                "R' U' F U R U' R' F' R",               // 31
                "L U F' U' L' U L F L'",                // 32
                "R U R' U' R' F R F'",                  // 33
                "R U R2 U' R' F R U R U' F'",           // 34
                "R U2 R2 F R F' R U2 R'",               // 35
                "L' U' L U' L' U L U L F' L' F",        // 36
                "F R' F' R U R U' R'",                  // 37
                "R U R' U R U' R' U' R' F R F'",        // 38
                "L F' L' U' L U F U' L'",               // 39
                "R' F R U R' U' F' U R",                // 40
                "R U R' U R U2 R' F R U R' U' F'",      // 41
                "R' U' R U' R' U2 R F R U R' U' F'",    // 42
                "F' U' L' U L F",                       // 43
                "F U R U' R' F'",                       // 44
                "F R U R' U' F'",                       // 45
                "R' U' R' F R F' U R",                  // 46
                "R' U' R' F R F' R' F R F' U R",        // 47
                "F R U R' U' R U R' U' F'",             // 48
                "r U' r2 U r2 U r2 U' r",               // 49
                "r' U r2 U' r2 U' r2 U r'",             // 50
                "F U R U' R' U R U' R' F'",             // 51
                "R U R' U R U' B U' B' R'",             // 52
                "l' U2 L U L' U' L U L' U l",           // 53
                "r U2 R' U' R U R' U' R U' r'",         // 54
                "R' F R U R U' R2 F' R2 U' R' U R U R'",// 55
                "r' U' r U' R' U R U' R' U R r' U r",   // 56
                "R U R' U' M' U R U' r'"                // 57
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
        var alg = Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm));
        var setup = alg.inverse().toString();
        var orientedCube = new OrientedCube();
        orientedCube.applyAlgorithm(NotationNormalizer.normalizePrimes(setup));
        return new OLLCase(
                OLLAnalyzer.extractSignature(orientedCube.cubeState(), orientedCube.orientation()),
                alg,
                name
        );
    }

    private static OLLCase caseFromSetup(String setup, String algorithm, String name) {
        var orientedCube = new OrientedCube();
        orientedCube.applyAlgorithm(NotationNormalizer.normalizePrimes(setup));
        return new OLLCase(
                OLLAnalyzer.extractSignature(orientedCube.cubeState(), orientedCube.orientation()),
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
