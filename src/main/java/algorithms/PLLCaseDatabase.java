package algorithms;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.PLLAnalyzer;
import cfop.PLLCaseSignature;
import cube.Algorithm;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.Move;
import cube.OrientedCube;
import util.NotationNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PLLCaseDatabase {
    private static final Algorithm[] FINAL_AUF_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME))
    };

    private final Map<LookupKey, List<PLLCase>> casesByLookup = new LinkedHashMap<>();
    private final List<PLLCase> caseList = new ArrayList<>();
    private int frameVariantCount;

    public static PLLCaseDatabase empty() {
        return new PLLCaseDatabase();
    }

    public static PLLCaseDatabase seedCases() {
        var database = new PLLCaseDatabase();
        for (var pllCase : seedCaseList()) {
            database.register(pllCase);
        }
        return database;
    }

    public static Map<LookupKey, List<PLLCase>> duplicateSeedCases() {
        return findDuplicateSignatures(seedCaseList());
    }

    private static List<PLLCase> seedCaseList() {
        var pllCases = new ArrayList<PLLCase>();
        var cases = List.of(
                "x R' U R' D2 R U' R' D2 R2 x'",
                "x R2 D2 R U R' D2 R U' R x'",
                "x' R U' R' D R U R' D' R U R' D R U' R' D' x",
                "R' U' F' R U R' U' R' F R2 U' R' U' R U R' U R",
                "R2 U R' U R' U' R U' R2 D U' R' U R D'",
                "R' U' R U D' R2 U R' U R U' R U' R2 D",
                "R2 U' R U' R U R' U R2 D' U R U' R' D",
                "R U R' U' D R2 U' R U' R' U R' U R2 D'",
                "M2 U M2 U2 M2 U M2",
                "L' U' L F L' U' L U L F' L2 U L",
                "R U R' F' R U R' U' R' F R2 U' R'",
                "R U R' U R U R' F' R U R' U' R' F R2 U' R' U2 R U' R'",
                "R' U R U' R' F' U' F R U R' F R' F' R U' R",
                "R U' R' U' R U R D R' U' R D' R' U2 R'",
                "R2 F R U R U' R' F' R U2 R' U2 R",
                "R U R' U' R' F R2 U' R' U' R U R' F'",
                "R U' R U R U R U' R' U' R2",
                "R2 U R U R' U' R' U' R' U R'",
                "R' U R' U' y R' F' R2 U' R' U R' F R F",
                "F R U' R' U' R U R' F' R U R' U' R' F R F'",
                "M2 U M2 U M' U2 M2 U2 M'"
        );
        int count = 1;
        for (var pllCase : cases) {
            pllCases.add(caseFromAlgorithm(pllCase, String.format("case-%d", count++)));
        }
        return List.copyOf(pllCases);
    }

    private static PLLCase caseFromAlgorithm(String algorithm, String name) {
        var parsedAlgorithm = parseLastLayerAlgorithm(algorithm);
        var setupCube = new OrientedCube();
        setupCube.applyMoves(parsedAlgorithm.inverse().getMoves());
        validateSeedVariant(setupCube, parsedAlgorithm, name);
        return new PLLCase(
                PLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation()),
                parsedAlgorithm,
                name
        );
    }

    private static PLLCase caseFromSetup(String setup, String algorithm, String name) {
        var parsedAlgorithm = parseLastLayerAlgorithm(algorithm);
        var setupCube = new OrientedCube();
        setupCube.applyAlgorithm(NotationNormalizer.normalizeLastLayerAlgorithm(setup));
        validateSeedVariant(setupCube, parsedAlgorithm, name);
        return new PLLCase(
                PLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation()),
                parsedAlgorithm,
                name
        );
    }

    public void register(String algorithm, String name) {
        register(caseFromAlgorithm(algorithm, name));
    }

    public void register(String setup, String algorithm, String name) {
        register(caseFromSetup(setup, algorithm, name));
    }

    public void register(PLLCase pllCase) {
        if (pllCase == null) {
            throw new IllegalArgumentException("pllCase cannot be null");
        }
        caseList.add(pllCase);
        for (var orientationKey : CubeOrientationKey.all()) {
            for (var finalAuf : FINAL_AUF_TRIALS) {
                var solution = pllCase.algorithm().concat(finalAuf);
                var setupCube = setupCubeFor(orientationKey, solution);
                validateSeedVariant(setupCube, solution, pllCase.name());
                var lookupKey = new LookupKey(
                        CubeOrientationKey.from(setupCube.orientation()),
                        PLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation())
                );
                casesByLookup.computeIfAbsent(lookupKey, ignored -> new ArrayList<>()).add(pllCase);
                frameVariantCount++;
            }
        }
    }

    public List<PLLCase> findAll(CubeOrientationKey orientationKey, PLLCaseSignature signature) {
        return List.copyOf(casesByLookup.getOrDefault(new LookupKey(orientationKey, signature), List.of()));
    }

    public int size() {
        return caseList.size();
    }

    public int frameVariantCount() {
        return frameVariantCount;
    }

    public Collection<PLLCase> allCases() {
        return List.copyOf(caseList);
    }

    private static void validateSeedVariant(OrientedCube setupCube, Algorithm algorithm, String name) {
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
        return Algorithm.materializeCubeRotations(
                Algorithm.parse(NotationNormalizer.normalizeLastLayerAlgorithm(algorithm))
        );
    }

    private static Map<LookupKey, List<PLLCase>> findDuplicateSignatures(Collection<PLLCase> pllCases) {
        var grouped = new LinkedHashMap<LookupKey, List<PLLCase>>();
        for (var pllCase : pllCases) {
            if (pllCase == null) {
                continue;
            }
            for (var orientationKey : CubeOrientationKey.all()) {
                for (var finalAuf : FINAL_AUF_TRIALS) {
                    var setupCube = setupCubeFor(orientationKey, pllCase.algorithm().concat(finalAuf));
                    var lookupKey = new LookupKey(
                            CubeOrientationKey.from(setupCube.orientation()),
                            PLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation())
                    );
                    grouped.computeIfAbsent(lookupKey, ignored -> new ArrayList<>()).add(pllCase);
                }
            }
        }

        var duplicates = new LinkedHashMap<LookupKey, List<PLLCase>>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(duplicates);
    }

    private static OrientedCube setupCubeFor(CubeOrientationKey setupOrientationKey, Algorithm algorithm) {
        var solvedOrientation = new OrientedCube(new CubeState(), setupOrientationKey.toOrientation());
        solvedOrientation.applyMoves(algorithm.getMoves());

        var setupCube = new OrientedCube(new CubeState(), solvedOrientation.orientation());
        setupCube.applyMoves(algorithm.inverse().getMoves());
        if (!CubeOrientationKey.from(setupCube.orientation()).equals(setupOrientationKey)) {
            throw new IllegalStateException("Failed to reconstruct setup orientation for " + setupOrientationKey);
        }
        return setupCube;
    }

    public record LookupKey(CubeOrientationKey orientationKey, PLLCaseSignature signature) {
        public LookupKey {
            if (orientationKey == null || signature == null) {
                throw new IllegalArgumentException("lookup key components cannot be null");
            }
        }
    }
}
