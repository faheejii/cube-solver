package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.CubeOrientationKey;
import cube.CubeState;
import cube.OrientedCube;
import util.NotationNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class F2LInsertCaseDatabase {
    private final Map<F2LInsertCaseKey, F2LInsertCase> cases = new LinkedHashMap<>();
    private final Map<LookupKey, List<F2LInsertCase>> casesByLookup = new LinkedHashMap<>();
    private boolean validated;

    public static F2LInsertCaseDatabase empty() {
        return new F2LInsertCaseDatabase();
    }

    public static F2LInsertCaseDatabase seedCases() {
        return AlgorithmCaseCatalog.insertDatabase();
    }

    public void register(String algorithm, F2LSlot insertSlot, String name) {
        register(caseFromAlgorithm(algorithm, insertSlot, name));
    }

    public void register(F2LInsertCase insertCase) {
        if (insertCase == null) {
            throw new IllegalArgumentException("insertCase cannot be null");
        }
        if (cases.containsKey(insertCase.key())) {
            throw new IllegalArgumentException("Duplicate F2L insert case key: " + insertCase.key());
        }
        cases.put(insertCase.key(), insertCase);
        var lookupKey = new LookupKey(insertCase.insertSlot(), insertCase.signature());
        casesByLookup.computeIfAbsent(lookupKey, ignored -> new ArrayList<>()).add(insertCase);
        indexSourceVariants(insertCase);
        validated = false;
    }

    public Optional<F2LInsertCase> find(F2LSlot insertSlot, F2LPreservationMask requiredPreservedSlots, F2LCaseSignature signature) {
        return findCompatible(insertSlot, requiredPreservedSlots, signature).stream().findFirst();
    }

    public List<F2LInsertCase> findCompatible(
            F2LSlot insertSlot,
            F2LPreservationMask requiredPreservedSlots,
            F2LCaseSignature signature
    ) {
        if (insertSlot == null) {
            throw new IllegalArgumentException("insertSlot cannot be null");
        }
        if (requiredPreservedSlots == null) {
            throw new IllegalArgumentException("requiredPreservedSlots cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }

        return compatibleCases(
                casesByLookup.getOrDefault(new LookupKey(insertSlot, signature), List.of()),
                requiredPreservedSlots
        );
    }

    private static List<F2LInsertCase> compatibleCases(
            List<F2LInsertCase> indexedCases,
            F2LPreservationMask requiredPreservedSlots
    ) {
        var matches = new ArrayList<F2LInsertCase>();
        for (var insertCase : indexedCases) {
            if (insertCase.preservedSlots().preservesAll(requiredPreservedSlots)) {
                matches.add(insertCase);
            }
        }
        matches.sort(Comparator
                .comparingInt((F2LInsertCase insertCase) -> insertCase.algorithm().getMoves().size())
                .thenComparingInt(insertCase -> Integer.bitCount(insertCase.preservedSlots().bits())));
        return List.copyOf(matches);
    }

    public int size() {
        return cases.size();
    }

    public Collection<F2LInsertCase> allCases() {
        return cases.values();
    }

    public void validate() {
        if (validated) {
            return;
        }
        for (var insertCase : cases.values()) {
            validateSeedCase(insertCase);
        }
        validated = true;
    }

    /** Insert cases are slot-specific, but their sticker coordinates are frame-relative. */
    private void indexSourceVariants(F2LInsertCase insertCase) {
        for (var frameKey : CubeOrientationKey.all()) {
            var source = new OrientedCube(new CubeState(), frameKey.toOrientation());
            source.applyMoves(insertCase.algorithm().inverse().getMoves());
            var signature = F2LCaseSignatureExtractor.extract(
                    source.cubeState(), insertCase.insertSlot(), source.orientation()
            );
            var key = new LookupKey(insertCase.insertSlot(), signature);
            var indexed = casesByLookup.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (!indexed.contains(insertCase)) {
                indexed.add(insertCase);
            }
        }
    }

    private static F2LInsertCase caseFromAlgorithm(
            String algorithm,
            F2LSlot insertSlot,
            String name
    ) {
        if (insertSlot == null) {
            throw new IllegalArgumentException("insertSlot cannot be null");
        }
        var alg = Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm));
        var mask = F2LPreservationMask.allExcept(insertSlot);
        var setupCube = new OrientedCube();
        setupCube.applyMoves(alg.inverse().getMoves());
        var signature = F2LCaseSignatureExtractor.extract(
                setupCube.cubeState(), insertSlot, setupCube.orientation()
        );
        return new F2LInsertCase(insertSlot, mask, signature, alg, name);
    }

    private static void validateSeedCase(F2LInsertCase insertCase) {
        var source = new OrientedCube();
        source.applyMoves(insertCase.algorithm().inverse().getMoves());
        var sourceOrientation = source.orientation();
        var insertedTarget = F2LGeometry.targetSlotFor(insertCase.insertSlot(), sourceOrientation);
        var preservedTargets = new ArrayList<F2LGeometry.TargetSlot>();

        if (!F2LGeometry.isTargetCrossSolved(source.cubeState(), F2LGeometry.targetCrossForOrientation(sourceOrientation))) {
            throw new IllegalArgumentException("F2L insert case source does not preserve cross: " + insertCase.name());
        }
        for (var preservedSlot : insertCase.preservedSlots().slots()) {
            var target = F2LGeometry.targetSlotFor(preservedSlot, sourceOrientation);
            if (!F2LGeometry.isTargetSlotSolved(source.cubeState(), target)) {
                throw new IllegalArgumentException("F2L insert case source does not preserve slot " + preservedSlot + ": " + insertCase.name());
            }
            preservedTargets.add(target);
        }

        source.applyMoves(insertCase.algorithm().getMoves());

        var orientation = source.orientation();
        if (!F2LGeometry.isTargetCrossSolved(source.cubeState(), F2LGeometry.targetCrossForOrientation(orientation))) {
            throw new IllegalArgumentException("F2L insert case does not preserve cross: " + insertCase.name());
        }

        if (!F2LGeometry.isTargetSlotSolved(source.cubeState(), insertedTarget)) {
            throw new IllegalArgumentException("F2L insert case does not solve insert slot: " + insertCase.name());
        }

        for (var target : preservedTargets) {
            if (!F2LGeometry.isTargetSlotSolved(source.cubeState(), target)) {
                throw new IllegalArgumentException("F2L insert case does not preserve slot " + target + ": " + insertCase.name());
            }
        }
    }

    private record LookupKey(F2LSlot insertSlot, F2LCaseSignature signature) {
    }

}
