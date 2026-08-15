package algorithms;

import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cfop.F2LSetupSignature;
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

public class F2LSetupCaseDatabase {
    private final Map<String, F2LSetupCase> cases = new LinkedHashMap<>();
    private final Map<F2LSetupSignature, List<F2LSetupCase>> casesBySignature = new LinkedHashMap<>();
    private boolean validated;

    public static F2LSetupCaseDatabase empty() {
        return new F2LSetupCaseDatabase();
    }

    public static F2LSetupCaseDatabase seedCases() {
        return AlgorithmCaseCatalog.setupDatabase();
    }

    public void register(String sourceSetup, String algorithm, F2LSlot nonPreservedSlot, String name) {
        var setupCase = casesFromSetup(sourceSetup, algorithm, nonPreservedSlot, name);
        // A source definition may not preserve the declared contract in the
        // current frame. Keep the catalog's established behavior of excluding
        // that definition, but never split a valid definition into one case
        // per possible insertion view.
        if (isValidSeedCase(setupCase)) {
            register(setupCase);
        }
    }

    public void register(F2LSetupCase setupCase) {
        if (setupCase == null) {
            throw new IllegalArgumentException("setupCase cannot be null");
        }
        if (cases.putIfAbsent(setupCase.name(), setupCase) == null) {
            casesBySignature.computeIfAbsent(setupCase.signature(), ignored -> new ArrayList<>()).add(setupCase);
            indexSourceVariants(setupCase);
            validated = false;
        }
    }

    /** Setup lookup is independent of the eventual insertion target. */
    public List<F2LSetupCase> findCompatible(
            F2LPreservationMask requiredPreservedSlots,
            F2LSetupSignature signature
    ) {
        if (requiredPreservedSlots == null) {
            throw new IllegalArgumentException("requiredPreservedSlots cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
        return compatibleCases(casesBySignature.getOrDefault(signature, List.of()), requiredPreservedSlots);
    }

    private static List<F2LSetupCase> compatibleCases(
            List<F2LSetupCase> indexedCases,
            F2LPreservationMask requiredPreservedSlots
    ) {
        var matches = new ArrayList<F2LSetupCase>();
        for (var setupCase : indexedCases) {
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

    private static F2LSetupCase casesFromSetup(
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
        var setupCube = new OrientedCube();
        setupCube.applyMoves(source.getMoves());
        var signature = F2LSetupSignature.from(
                F2LCaseSignatureExtractor.extract(
                        setupCube.cubeState(), nonPreservedSlot, setupCube.orientation()
                )
        );

        return new F2LSetupCase(
                mask, nonPreservedSlot, signature, alg, source, name
        );
    }

    /** Index authored source states in every legal frame and pair view. */
    private void indexSourceVariants(F2LSetupCase setupCase) {
        for (var frameKey : CubeOrientationKey.all()) {
            var source = new OrientedCube(new CubeState(), frameKey.toOrientation());
            source.applyMoves(setupCase.sourceSetup().getMoves());
            for (var slot : F2LSlot.values()) {
                var signature = F2LSetupSignature.from(
                        F2LCaseSignatureExtractor.extract(source.cubeState(), slot, source.orientation())
                );
                var indexed = casesBySignature.computeIfAbsent(signature, ignored -> new ArrayList<>());
                if (!indexed.contains(setupCase)) {
                    indexed.add(setupCase);
                }
            }
        }
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

}
