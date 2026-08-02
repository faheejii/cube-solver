package algorithms;

import cfop.CrossAnalyzer;
import cfop.F2LAnalyzer;
import cfop.OLLAnalyzer;
import cfop.OLLCaseSignature;
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

public class OLLCaseDatabase {
    private final Map<LookupKey, List<OLLCase>> casesByLookup = new LinkedHashMap<>();
    private final List<OLLCase> caseList = new ArrayList<>();
    private int frameVariantCount;

    public static OLLCaseDatabase empty() {
        return new OLLCaseDatabase();
    }

    public static OLLCaseDatabase seedCases() {
        return AlgorithmCaseCatalog.ollDatabase();
    }

    public static Map<LookupKey, List<OLLCase>> duplicateSeedCases() {
        return findDuplicateSignatures(AlgorithmCaseCatalog.ollDatabase().allCases());
    }

    private static OLLCase caseFromAlgorithm(String algorithm, String name) {
        return caseFromAlgorithm(parseLastLayerAlgorithm(algorithm), name);
    }

    private static OLLCase caseFromAlgorithm(Algorithm algorithm, String name) {
        var setupCube = new OrientedCube();
        setupCube.applyMoves(algorithm.inverse().getMoves());
        validateSeedVariant(setupCube, algorithm, name);
        return new OLLCase(
                OLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation()),
                algorithm,
                name
        );
    }

    private static OLLCase caseFromSetup(String setup, String algorithm, String name) {
        var parsedAlgorithm = parseLastLayerAlgorithm(algorithm);
        var setupCube = new OrientedCube();
        setupCube.applyAlgorithm(NotationNormalizer.normalizeLastLayerAlgorithm(setup));
        validateSeedVariant(setupCube, parsedAlgorithm, name);
        return new OLLCase(
                OLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation()),
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

    public void register(OLLCase ollCase) {
        if (ollCase == null) {
            throw new IllegalArgumentException("ollCase cannot be null");
        }
        caseList.add(ollCase);
        for (var orientationKey : CubeOrientationKey.all()) {
            var setupCube = setupCubeFor(orientationKey, ollCase.algorithm());
            validateSeedVariant(setupCube, ollCase.algorithm(), ollCase.name());
            var lookupKey = new LookupKey(
                    CubeOrientationKey.from(setupCube.orientation()),
                    OLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation())
            );
            casesByLookup.computeIfAbsent(lookupKey, ignored -> new ArrayList<>()).add(ollCase);
            frameVariantCount++;
        }
    }

    public List<OLLCase> findAll(CubeOrientationKey orientationKey, OLLCaseSignature signature) {
        return List.copyOf(casesByLookup.getOrDefault(new LookupKey(orientationKey, signature), List.of()));
    }

    public int size() {
        return caseList.size();
    }

    public int frameVariantCount() {
        return frameVariantCount;
    }

    public int lookupSignatureCount(CubeOrientationKey orientationKey) {
        return (int) casesByLookup.keySet().stream()
                .filter(key -> key.orientationKey().equals(orientationKey))
                .count();
    }

    public Collection<OLLCase> allCases() {
        return List.copyOf(caseList);
    }

    public void validate() {
        for (var ollCase : caseList) {
            for (var move : ollCase.algorithm().getMoves()) {
                if (move.isCubeRotation()) {
                    throw new IllegalArgumentException("OLL DB algorithms must not contain cube rotations: " + ollCase.name());
                }
            }
            for (var orientationKey : CubeOrientationKey.all()) {
                var setupCube = setupCubeFor(orientationKey, ollCase.algorithm());
                validateSeedVariant(setupCube, ollCase.algorithm(), ollCase.name());
            }
        }
    }

    private static void validateSeedVariant(OrientedCube setupCube, Algorithm algorithm, String name) {
        var solvedCube = new OrientedCube(setupCube.cubeState().copy(), setupCube.orientation());
        solvedCube.applyMoves(algorithm.getMoves());
        if (!CrossAnalyzer.isCrossSolved(solvedCube.cubeState(), solvedCube.orientation())) {
            throw new IllegalArgumentException("OLL algorithm must preserve cross: " + name);
        }
        if (!F2LAnalyzer.isF2LSolved(solvedCube.cubeState(), solvedCube.orientation())) {
            throw new IllegalArgumentException("OLL algorithm must preserve F2L: " + name);
        }
        if (!OLLAnalyzer.isOllSolved(solvedCube.cubeState(), solvedCube.orientation())) {
            throw new IllegalArgumentException("OLL algorithm must solve OLL: " + name);
        }
    }

    private static Algorithm parseLastLayerAlgorithm(String algorithm) {
        return Algorithm.materializeWideAndSliceMoves(
                Algorithm.parse(NotationNormalizer.normalizeLastLayerAlgorithm(algorithm))
        );
    }

    private static Map<LookupKey, List<OLLCase>> findDuplicateSignatures(Collection<OLLCase> ollCases) {
        var grouped = new LinkedHashMap<LookupKey, List<OLLCase>>();
        for (var ollCase : ollCases) {
            if (ollCase == null) {
                continue;
            }
            for (var orientationKey : CubeOrientationKey.all()) {
                var setupCube = setupCubeFor(orientationKey, ollCase.algorithm());
                var lookupKey = new LookupKey(
                        CubeOrientationKey.from(setupCube.orientation()),
                        OLLAnalyzer.extractSignature(setupCube.cubeState(), setupCube.orientation())
                );
                grouped.computeIfAbsent(lookupKey, ignored -> new ArrayList<>()).add(ollCase);
            }
        }

        var duplicates = new LinkedHashMap<LookupKey, List<OLLCase>>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        return Map.copyOf(duplicates);
    }

    private static OrientedCube setupCubeFor(CubeOrientationKey setupOrientationKey, Algorithm algorithm) {
        var setupCube = new OrientedCube(new CubeState(), setupOrientationKey.toOrientation());
        setupCube.applyMoves(algorithm.inverse().getMoves());
        return setupCube;
    }

    public record LookupKey(CubeOrientationKey orientationKey, OLLCaseSignature signature) {
        public LookupKey {
            if (orientationKey == null || signature == null) {
                throw new IllegalArgumentException("lookup key components cannot be null");
            }
        }
    }
}
