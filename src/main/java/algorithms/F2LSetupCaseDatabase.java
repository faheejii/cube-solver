package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.OrientedCube;
import util.NotationNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class F2LSetupCaseDatabase {
    private final Map<F2LSetupCaseKey, F2LSetupCase> cases = new LinkedHashMap<>();
    private final Map<LookupKey, List<F2LSetupCase>> casesByLookup = new LinkedHashMap<>();
    private boolean validated;

    public static F2LSetupCaseDatabase empty() {
        return new F2LSetupCaseDatabase();
    }

    public static F2LSetupCaseDatabase seedCases() {
        return AlgorithmCaseCatalog.setupDatabase();
    }

    public void register(String sourceSetup, String algorithm, F2LSlot nonPreservedSlot, String name) {
        for (var setupCase : casesFromSetup(sourceSetup, algorithm, nonPreservedSlot, name)) {
            register(setupCase);
        }
    }

    public void register(F2LSetupCase setupCase) {
        if (setupCase == null) {
            throw new IllegalArgumentException("setupCase cannot be null");
        }
        if (cases.putIfAbsent(setupCase.key(), setupCase) == null) {
            var lookupKey = new LookupKey(setupCase.insertSlot(), setupCase.signature());
            casesByLookup.computeIfAbsent(lookupKey, ignored -> new ArrayList<>()).add(setupCase);
            validated = false;
        }
    }

    public Optional<F2LSetupCase> find(F2LSlot insertSlot, F2LPreservationMask requiredPreservedSlots, F2LCaseSignature signature) {
        return findCompatible(insertSlot, requiredPreservedSlots, signature).stream().findFirst();
    }

    public List<F2LSetupCase> findCompatible(
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

        var matches = new ArrayList<F2LSetupCase>();
        for (var setupCase : casesByLookup.getOrDefault(new LookupKey(insertSlot, signature), List.of())) {
            if (setupCase.preservedSlots().preservesAll(requiredPreservedSlots)) {
                matches.add(setupCase);
            }
        }
        matches.sort(Comparator
                .comparingInt((F2LSetupCase setupCase) -> setupCase.algorithm().getMoves().size())
                .thenComparingInt(setupCase -> Integer.bitCount(setupCase.preservedSlots().bits())));
        return List.copyOf(matches);
    }

    public int size() {
        return cases.size();
    }

    public Collection<F2LSetupCase> allCases() {
        return cases.values();
    }

    public void validate() {
        if (validated) {
            return;
        }
        for (var setupCase : cases.values()) {
            validateSeedCase(setupCase);
        }
        validated = true;
    }

    private static List<F2LSetupCase> casesFromSetup(
            String sourceSetup,
            String algorithm,
            F2LSlot nonPreservedSlot,
            String name
    ) {
        if (sourceSetup == null) {
            throw new IllegalArgumentException("sourceSetup cannot be null");
        }
        if (nonPreservedSlot == null) {
            throw new IllegalArgumentException("nonPreservedSlot cannot be null");
        }
        var source = Algorithm.parse(NotationNormalizer.normalizePrimes(sourceSetup));
        var alg = Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm));
        var mask = F2LPreservationMask.allExcept(nonPreservedSlot);
        var setupCases = new ArrayList<F2LSetupCase>();

        for (var insertSlot : F2LSlot.values()) {
            var setupCube = new OrientedCube();
            setupCube.applyMoves(source.getMoves());
            var signature = F2LCaseSignatureExtractor.extract(setupCube.cubeState(), insertSlot, setupCube.orientation());
            var setupCase = new F2LSetupCase(insertSlot, mask, signature, alg, source, name + "-" + insertSlot);
            if (isValidSeedCase(setupCase)) {
                setupCases.add(setupCase);
            }
        }

        return List.copyOf(setupCases);
    }

    private static void validateSeedCase(F2LSetupCase setupCase) {
        var validationError = validateSeedCaseError(setupCase);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }
    }

    private static boolean isValidSeedCase(F2LSetupCase setupCase) {
        return validateSeedCaseError(setupCase) == null;
    }

    private static String validateSeedCaseError(F2LSetupCase setupCase) {
        var source = new OrientedCube();
        source.applyMoves(setupCase.sourceSetup().getMoves());
        var sourceOrientation = source.orientation();
        var preservedTargets = new ArrayList<F2LGeometry.TargetSlot>();

        if (!F2LGeometry.isTargetCrossSolved(source.cubeState(), F2LGeometry.targetCrossForOrientation(sourceOrientation))) {
            return "F2L setup case source does not preserve cross: " + setupCase.name();
        }
        for (var preservedSlot : setupCase.preservedSlots().slots()) {
            var target = F2LGeometry.targetSlotFor(preservedSlot, sourceOrientation);
            if (!F2LGeometry.isTargetSlotSolved(source.cubeState(), target)) {
                return "F2L setup case source does not preserve slot " + preservedSlot + ": " + setupCase.name();
            }
            preservedTargets.add(target);
        }

        source.applyMoves(setupCase.algorithm().getMoves());

        var orientation = source.orientation();
        if (!F2LGeometry.isTargetCrossSolved(source.cubeState(), F2LGeometry.targetCrossForOrientation(orientation))) {
            return "F2L setup case does not preserve cross: " + setupCase.name();
        }

        for (var target : preservedTargets) {
            if (!F2LGeometry.isTargetSlotSolved(source.cubeState(), target)) {
                return "F2L setup case does not preserve slot " + target + ": " + setupCase.name();
            }
        }
        return null;
    }

    private record LookupKey(F2LSlot insertSlot, F2LCaseSignature signature) {
    }
}
