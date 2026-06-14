package algorithms;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cfop.PLLCaseSignature;
import cube.Algorithm;
import cube.Move;
import cube.OrientedCube;
import util.NotationNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PLLCaseDatabase {
    private static final Algorithm[] FINAL_AUF_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME))
    };

    private final Map<PLLCaseSignature, PLLCase> cases = new LinkedHashMap<>();

    public static PLLCaseDatabase empty() {
        return new PLLCaseDatabase();
    }

    public static PLLCaseDatabase seedCases() {
        var database = new PLLCaseDatabase();
        for (var pllCase : normalizeBySignatureKeepingFirst(seedCaseList())) {
            database.register(pllCase);
        }
        return database;
    }

    public static Map<PLLCaseSignature, List<PLLCase>> duplicateSeedCases() {
        return findDuplicateSignatures(seedCaseList());
    }

    private static List<PLLCase> seedCaseList() {
        var pllCases = new ArrayList<PLLCase>();
        var cases = List.of(
                "x R' U R' D2 R U' R' D2 R2 x'",                 // Aa
                "x R2 D2 R U R' D2 R U' R x'",                            // Ab
                "x' R U' R' D R U R' D' R U R' D R U' R' D' x",           // E
                "R' U' F' R U R' U' R' F R2 U' R' U' R U R' U R",         // F
                "R2 U R' U R' U' R U' R2 D U' R' U R D'",                 // Ga
                "R' U' R U D' R2 U R' U R U' R U' R2 D",                  // Gb
                "R2 U' R U' R U R' U R2 D' U R U' R' D",                  // Gc
                "R U R' U' D R2 U' R U' R' U R' U R2 D'",                 // Gd
                "M2 U M2 U2 M2 U M2",                                      // H
                "L' U' L F L' U' L U L F' L2 U L",                        // Ja
                "R U R' F' R U R' U' R' F R2 U' R'",                      // Jb
                "R U R' U R U R' F' R U R' U' R' F R2 U' R' U2 R U' R'",  // Na
                "R' U R U' R' F' U' F R U R' F R' F' R U' R",             // Nb
                "R U' R' U' R U R D R' U' R D' R' U2 R'",                 // Ra
                "R2 F R U R U' R' F' R U2 R' U2 R",                       // Rb
                "R U R' U' R' F R2 U' R' U' R U R' F'",                   // T
                "R U' R U R U R U' R' U' R2",                             // Ua
                "R2 U R U R' U' R' U' R' U R'",                           // Ub
                "R' U R' U' y R' F' R2 U' R' U R' F R F",                 // V
                "F R U' R' U' R U R' F' R U R' U' R' F R F'",             // Y
                "M2 U M2 U M' U2 M2 U2 M'"                              // Z
        );
        int count = 1;
        for (var pllCase : cases) {
            pllCases.addAll(casesFromAlgorithm(pllCase, String.format("case-%d", count++)));
        }
        return List.copyOf(pllCases);
    }

    private static List<PLLCase> casesFromAlgorithm(String algorithm, String name) {
        var alg = parseLastLayerAlgorithm(algorithm);
        var pllCases = new ArrayList<PLLCase>();
        for (var finalAuf : FINAL_AUF_TRIALS) {
            var setup = alg.concat(finalAuf).inverse().toString();
            var orientedCube = new OrientedCube();
            orientedCube.applyAlgorithm(setup);
            validateSetupThenAlgorithm(orientedCube, alg.concat(finalAuf), name);
            pllCases.add(new PLLCase(
                    PLLAnalyzer.extractSignature(orientedCube.cubeState(), orientedCube.orientation()),
                    alg,
                    name
            ));
        }
        return List.copyOf(pllCases);
    }

    private static PLLCase caseFromSetup(String setup, String algorithm, String name) {
        var alg = parseLastLayerAlgorithm(algorithm);
        var orientedCube = new OrientedCube();
        orientedCube.applyAlgorithm(NotationNormalizer.normalizeLastLayerAlgorithm(setup));
        validateSetupThenAlgorithm(orientedCube, alg, name);
        return new PLLCase(
                PLLAnalyzer.extractSignature(orientedCube.cubeState(), orientedCube.orientation()),
                alg,
                name
        );
    }

    public void register(String algorithm, String name) {
        for (var pllCase : casesFromAlgorithm(algorithm, name)) {
            register(pllCase);
        }
    }

    public void register(String setup, String algorithm, String name) {
        register(caseFromSetup(setup, algorithm, name));
    }

    public void register(PLLCase pllCase) {
        if (pllCase == null) {
            throw new IllegalArgumentException("pllCase cannot be null");
        }
        if (cases.containsKey(pllCase.signature())) {
            throw new IllegalArgumentException("Duplicate PLL case signature: " + pllCase.signature());
        }
        cases.put(pllCase.signature(), pllCase);
    }

    public Optional<PLLCase> find(PLLCaseSignature signature) {
        return Optional.ofNullable(cases.get(signature));
    }

    public int size() {
        return cases.size();
    }

    public Collection<PLLCase> allCases() {
        return cases.values();
    }

    private static void validateSetupThenAlgorithm(OrientedCube setupCube, Algorithm algorithm, String name) {
        if (!CrossAnalyzer.isCrossSolved(setupCube.cubeState(), setupCube.orientation())) {
            throw new IllegalArgumentException("PLL setup must preserve cross: " + name);
        }
        if (!F2LAnalyzer.isF2LSolved(setupCube.cubeState(), setupCube.orientation())) {
            throw new IllegalArgumentException("PLL setup must preserve F2L: " + name);
        }
        if (!OLLAnalyzer.isOllSolved(setupCube.cubeState(), setupCube.orientation())) {
            throw new IllegalArgumentException("PLL setup must preserve OLL: " + name);
        }

        var solvedCube = new OrientedCube(setupCube.cubeState().copy(), setupCube.orientation());
        solvedCube.applyMoves(algorithm.getMoves());
        if (!CrossAnalyzer.isCrossSolved(solvedCube.cubeState(), solvedCube.orientation())) {
            throw new IllegalArgumentException("PLL algorithm must preserve cross: " + name);
        }
        if (!F2LAnalyzer.isF2LSolved(solvedCube.cubeState(), solvedCube.orientation())) {
            throw new IllegalArgumentException("PLL algorithm must preserve F2L: " + name);
        }
        if (!OLLAnalyzer.isOllSolved(solvedCube.cubeState(), solvedCube.orientation())) {
            throw new IllegalArgumentException("PLL algorithm must preserve OLL: " + name);
        }
        if (!PLLAnalyzer.isPllSolved(solvedCube.cubeState(), solvedCube.orientation())) {
            throw new IllegalArgumentException("PLL algorithm must solve PLL: " + name);
        }
    }

    private static Algorithm parseLastLayerAlgorithm(String algorithm) {
        var executable = Algorithm.parse(NotationNormalizer.normalizeLastLayerAlgorithm(algorithm));
        return Algorithm.fromMoves(executable.getMoves(), NotationNormalizer.normalizePrimes(algorithm));
    }

    private static List<PLLCase> normalizeBySignatureKeepingFirst(Collection<PLLCase> pllCases) {
        var deduped = new LinkedHashMap<PLLCaseSignature, PLLCase>();
        for (var pllCase : pllCases) {
            deduped.putIfAbsent(pllCase.signature(), pllCase);
        }
        return List.copyOf(deduped.values());
    }

    private static Map<PLLCaseSignature, List<PLLCase>> findDuplicateSignatures(Collection<PLLCase> pllCases) {
        var grouped = new LinkedHashMap<PLLCaseSignature, List<PLLCase>>();
        for (var pllCase : pllCases) {
            if (pllCase == null) {
                continue;
            }
            grouped.computeIfAbsent(pllCase.signature(), ignored -> new ArrayList<>()).add(pllCase);
        }

        var duplicates = new LinkedHashMap<PLLCaseSignature, List<PLLCase>>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(duplicates);
    }
}
