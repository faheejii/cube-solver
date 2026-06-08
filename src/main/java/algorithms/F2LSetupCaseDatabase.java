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

    public static F2LSetupCaseDatabase empty() {
        return new F2LSetupCaseDatabase();
    }

    public static F2LSetupCaseDatabase seedCases() {
        var database = new F2LSetupCaseDatabase();
        for (var setupCase : seedCaseList()) {
            database.register(setupCase);
        }
        return database;
    }

    private static List<F2LSetupCase> seedCaseList() {
        var cases = new SeedCaseList();
        var FR = F2LSlot.FR;
        var BR = F2LSlot.BR;
        var FL = F2LSlot.FL;
        var BL = F2LSlot.BL;

        cases.add("R U R' U2 R U' R'", "R U R'", FR, "case-1.1");
        cases.add("L U L' U2 L U' L'", "L U L'", BL, "case-1.2");

        cases.add("L' U' L U2 L' U L", "L' U' L", FL, "case-2.1");
        cases.add("R' U' R U2 R' U R", "R' U' R", BR, "case-2.2");

        cases.add("R U R' U2 R U2 R'", "R U2 R'", FR, "case-3.1");
        cases.add("L U L' U2 L U2 L'", "L U2 L'", BL, "case-3.2");

        cases.add("R' U' R U2 R' U2 R", "R' U2 R", BR, "case-4.1");
        cases.add("L' U' L U2 L' U2 L", "L' U2 L", FL, "case-4.2");

        cases.add("F' U F U' R U R'", "R U' R'", FR, "case-5.1");
        cases.add("F' U F U' R U R' U2", "R U R'", FR, "case-5.2");
        cases.add("R' U R U' R' U' R", "R' U R", BR, "case-5.3");
        cases.add("L' U L U' L' U' L", "L' U L", FL, "case-5.4");
        cases.add("B' U B U2 L U' L'", "L U L'", BL, "case-5.5");
        cases.add("B' U B U' L U L'", "L U' L'", BL, "case-5.6");
        cases.add("R' U R U' R' U' R U'", "R' U' R", BR, "case-5.7");
        cases.add("L' U L U' L' U' L U'", "L' U' L", FL, "case-5.8");

        cases.add("R U' R' U' R U' R'", "R U R'", FR, "case-6.1");
        cases.add("R U' R' U' R U' R' U'", "R U' R'", FR, "case-6.2");
        cases.add("R U' R' U' R U' R' U2 y'", "R' U R", BR, "case-6.3");
        cases.add("R U' R' U' R U' R' y'", "R' U' R", BR, "case-6.4");
        cases.add("R U' R' U' R U' R' y", "L' U' L", FL, "case-6.5");
        cases.add("R U' R' U' R U' R' U2 y", "L' U L", FL, "case-6.6");
        cases.add("R U' R' U' R U' R' y2", "L U L'", BL, "case-6.7");
        cases.add("R U' R' U' R U' R' U' y2", "L U' L'", BL, "case-6.8");

        cases.add("F' U F U' R U2 R'", "R U2 R'", FR, "case-7.1");
        cases.add("F' U F U' R U2 R' y2", "L U2 L'", BL, "case-7.2");

        cases.add("R U R' U2 R U R' U' R U R' U y'", "R' U2 R", BR, "case-8.1");
        cases.add("R U R' U2 R U R' U' R U R' U y", "L' U2 L", FR, "case-8.2");

        cases.add("r U2 R' U R U' R' U M U y'", "R' U R", BR, "case-9.1");
        cases.add("r U2 R' U R U' R' U M U y", "L' U L", FL, "case-9.2");

        cases.add("R U' R' U' R U R'", "R U R'", FR, "case-10.1");
        cases.add("R U' R' U' R U R' y2", "L U' L'", BL, "case-10.2");

        cases.add("R U R' U' R U R' U2 R U' R'", "F' U L' U L F", FR, "case-11.1");
        cases.add("R U R' U' R U R' U2 R U' R' y'", "R' U R", BR, "case-11.2");
        cases.add("R U R' U' R U R' U2 R U' R' y", "L' U L", FL, "case-11.3");

        cases.add("F' U F U2 R U R'", "R U' R'", FR, "case-12.1");
        cases.add("F' U F U2 R U R' y", "F U' R U' R' F'", FL, "case-12.2");
        cases.add("F' U F U2 R U R' y2", "L U' L'", BL, "case-12.2");

        cases.add("R U' R' U R U2 R'", "R U2 R'", FR, "case-13.1");
        cases.add("R U' R' U R U2 R' y2", "L U2 L'", BL, "case-13.2");

        cases.add("R U R' U' R U R' F R' F' R y", "L' U2 L", FL, "case-14.1");
        cases.add("R U R' U' R U R' F R' F' R y'", "R' U2 R", BR, "case-14.2");

        cases.add("R U R' U' R U2 R'", "R U2 R'", FR, "case-15.1");
        cases.add("R U R' U' R U2 R' y2", "L U2 L'", BL, "case-15.2");

        cases.add("R U R' F R' F' R2 U R' y", "L' U2 L", FL, "case-16.1");
        cases.add("R U R' F R' F' R2 U R' y'", "R' U2 R", BR, "case-16.2");

        cases.add("R U' R' U2 R U R' U2", "R U R'", FR, "case-17.1");
        cases.add("R U' R' U2 R U R' U2 y2", "L U L'", BL, "case-17.2");
        cases.add("R U' R' U2 R U R' U2 y", "F U F'", FL, "case-17.3");

        cases.add("F' L' U2 L F U2", "F' U' F", FR, "case-18.1");
        cases.add("F' L' U2 L F U2 y'", "R' U' R", BR, "case-18.2");
        cases.add("F' L' U2 L F U2 y", "L' U' L", FL, "case-18.3");

        cases.add("R U' R' U R U' R' U2 R U' R' U", "R U' R' U' R U' R'", FR, "case-19.1");
        cases.add("R U' R' U R U' R' U2 R U' R' y'", "R' U' R U' R' U' R", BR, "case-19.2");
        cases.add("R U' R' U R U' R' U2 R U' R'", "R U R' U2 R U R'", FR, "case-19.3");
        cases.add("R U' R' U R U' R' U2 R U' R' y", "L' U' L U' L' U' L", FL, "case-19.4");

        cases.add("R U R' F R U R' U' F'", "R U R' U R U R'", FR, "case-20.1");
        cases.add("R U R' F R U R' U' F' U' y", "L' U L U L' U L", FL, "case-20.2");
        cases.add("R U R' F R U R' U' F' y2", "L U L' U L U L'", BL, "case-20.3");
        cases.add("R U R' F R U R' U' F' y", "L' U' L U2 L' U' L", FL, "case-20.2");

        cases.add("F' U' F U R U R'", "R U' R'", FR, "case-21.1");

        cases.add("R U R' U' R U R'", "R U' R'", FR, "case-22.1");
        cases.add("R U R' U' R U R' y", "L' U' L", FL, "case-22.2");
        cases.add("R U R' U' R U R' y'", "R' U' R", BR, "case-22.3");
        cases.add("R U R' U' R U R' y2", "L U' L'", BL, "case-22.4");

        cases.add("R' F R F' U R U' R'", "R U R'", FR, "case-23.1");
        cases.add("R' F R F' U R U' R' y", "L' U L", FL, "case-23.2");
        cases.add("R' F R F' U R U' R' y'", "R' U R", BR, "case-23.3");
        cases.add("R' F R F' U R U' R' y2", "L U L'", BL, "case-23.4");

        cases.add("F R' F' R F R' F' R", "R' F R F'", FR, "case-24.1");
        cases.add("F R' F' R F R' F' R y", "L' U' L", FL, "case-24.2");
        cases.add("F R' F' R F R' F' R y'", "R' U' R", BR, "case-24.3");

        cases.add("R U' R' U R U' R'", "R U R'", FR, "case-25.1");
        cases.add("R U' R' U R U' R' y2", "L' U L", BL, "case-25.2");
        cases.add("R U' R' U R U' R' y", "L F' L' F", FL, "case-25.3");

        cases.add("R U R' F R' F' R U", "R U' R'", FR, "case-26.1");
        cases.add("R U R' F R' F' R U y", "L' U L", FL, "case-26.2");
        cases.add("R U R' F R' F' R U y'", "R' U R", BR, "case-26.3");
        cases.add("R U R' F R' F' R U y2", "L U' L'", BL, "case-26.4");

        cases.add("R U' R' U R U' R' U R U' R' U", "R U' R' U R U' R'", FR, "case-27.1");
        cases.add("R U' R' U R U' R' U R U' R' U", "R' F R F'", FR, "case-27.2");
        cases.add("R U' R' U R U' R' U R U' R' U' y", "L F' L' F", FL, "case-27.3");

        cases.add("R U R' U2 R U R'", "R U' R'", FR, "case-28.1");
        cases.add("R U R' U2 R U R' y2", "L U' L'", BL, "case-28.2");

        cases.add("R U' R' U2 R U' R'", "R U R'", FR, "case-29.1");
        cases.add("R U' R' U2 R U' R' y", "L' U L", FL, "case-29.2");
        cases.add("R U' R' U2 R U' R' y'", "R' U R", BR, "case-29.3");

        cases.add("F' U F U' R U' R' U'", "R U R'", FR, "case-30.1");
        cases.add("F' U F U' R U' R' U' y2", "L U L'", BL, "case-30.2");
        cases.add("F' U F U' R U' R'", "R U R'", FR, "case-30.3");
        cases.add("F' U F U' R U' R' y2", "L U L'", BL, "case-30.4");

        cases.add("R U' R' U2 F R' F' R y", "L' U' L", FL, "case-31.1");
        cases.add("R U' R' U2 F R' F' R U' y", "L' U' L", FL, "case-31.2");
        cases.add("R U' R' U2 F R' F' R U' y'", "R' U' R", BR, "case-31.3");
        cases.add("R U' R' U2 F R' F' R y'", "R' U' R", BR, "case-31.4");

        cases.add("R U' R U2 F R2 F' U2 R2", "R U R' U2 R U2 R'", FR, "case-32.1");
        cases.add("R U' R U2 F R2 F' U2 R2 y2", "L' U L U2 L U2 L'", BL, "case-32.2");

        cases.add("R U' R' U R U2 R' U R U' R'", "R U' R' U' R U R'", FR, "case-33.1");
        cases.add("R U' R' U R U2 R' U R U' R' y", "L' U L U' L' U2 L", FL, "case-33.2");
        cases.add("R U' R' U R U2 R' U R U' R' y'", "R' U R U' R' U2 R", BR, "case-33.3");
        cases.add("R U' R' U R U2 R' U R U' R' y2", "L U' L' U' L U L'", BL, "case-33.4");

        cases.add("R U' R' U' R U R' U2 R U' R'", "R U' R' U R U2 R'", FR, "case-34.1");
        cases.add("R U' R' U' R U R' U2 R U' R' y2", "L U' L' U L U2 L'", BL, "case-34.2");
        cases.add("R U' R' U' R U R' U2 R U' R' y", "L' U L U L' U' L", FL, "case-34.3");
        cases.add("R U' R' U' R U R' U2 R U' R' y'", "R' U R U R' U' R", BR, "case-34.4");

        cases.add("R U R' F U R U' R' F' R U R'", "R U' R' U' R U' R'", FR, "case-35.1");
        cases.add("R U R' F U R U' R' F' R U R' y2", "L U' L' U' L U' L'", BL, "case-35.2");

        cases.add("R F U R U' R' F' U' R' y", "L' U L U L' U L", FL, "case-36.1");
        cases.add("R F U R U' R' F' U' R' y'", "R' U R U R' U R", BR, "case-36.2");
        return cases.toList();
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
        cases.putIfAbsent(setupCase.key(), setupCase);
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
        for (var setupCase : cases.values()) {
            if (setupCase.insertSlot() == insertSlot
                    && setupCase.signature().equals(signature)
                    && setupCase.preservedSlots().preservesAll(requiredPreservedSlots)) {
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
        for (var setupCase : cases.values()) {
            validateSeedCase(setupCase);
        }
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

    private static final class SeedCaseList {
        private final List<F2LSetupCase> cases = new ArrayList<>();

        private void add(String sourceSetup, String algorithm, F2LSlot nonPreservedSlot, String name) {
            cases.addAll(casesFromSetup(sourceSetup, algorithm, nonPreservedSlot, name));
        }

        private List<F2LSetupCase> toList() {
            return List.copyOf(cases);
        }
    }
}
