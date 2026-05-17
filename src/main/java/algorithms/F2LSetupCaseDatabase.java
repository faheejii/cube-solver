package algorithms;

import cfop.F2LCaseSignature;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.Move;
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
    private static final Algorithm[] INSERT_AUF_TRIALS = {
            new Algorithm(),
            Algorithm.fromMoves(List.of(Move.U)),
            Algorithm.fromMoves(List.of(Move.U2)),
            Algorithm.fromMoves(List.of(Move.U_PRIME))
    };
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
        var fr = F2LSlot.FR;
        var br = F2LSlot.BR;
        var fl = F2LSlot.FL;
        var bl = F2LSlot.BL;

        // Add setup cases here. Each algorithm should connect the target pair while preserving the listed slots.
        cases.add("R U R'", fr, List.of(br, fl, bl), "case-1.1");
        cases.add("R U R'", fr, List.of(br), "case-1.2");
        cases.add("R U R'", fr, List.of(br, fl), "case-1.3");
        cases.add("R U R'", fr, List.of(br, bl), "case-1.4");
        cases.add("R U R'", fr, List.of(fl), "case-1.5");
        cases.add("R U R'", fr, List.of(fl, bl), "case-1.6");
        cases.add("R U R'", fr, List.of(bl), "case-1.7");

        return cases.toList();
    }

    public void register(String algorithm, F2LSlot insertSlot, Collection<F2LSlot> preservedSlots, String name) {
        for (var setupCase : casesFromAlgorithm(algorithm, insertSlot, preservedSlots, name)) {
            register(setupCase);
        }
    }

    public void register(F2LSetupCase setupCase) {
        if (setupCase == null) {
            throw new IllegalArgumentException("setupCase cannot be null");
        }
        if (cases.containsKey(setupCase.key())) {
            throw new IllegalArgumentException("Duplicate F2L setup case key: " + setupCase.key());
        }
        cases.put(setupCase.key(), setupCase);
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

    private static List<F2LSetupCase> casesFromAlgorithm(
            String algorithm,
            F2LSlot insertSlot,
            Collection<F2LSlot> preservedSlots,
            String name
    ) {
        if (insertSlot == null) {
            throw new IllegalArgumentException("insertSlot cannot be null");
        }
        var alg = Algorithm.parse(NotationNormalizer.normalizePrimes(algorithm));
        var mask = F2LPreservationMask.of(preservedSlots);
        var setupCases = new ArrayList<F2LSetupCase>();
        var connectedReference = canonicalConnectedReference(insertSlot);

        for (var insertAuf : INSERT_AUF_TRIALS) {
            var sourceSetup = connectedReference
                    .concat(insertAuf.inverse())
                    .concat(alg.inverse());
            var setupCube = new OrientedCube();
            setupCube.applyMoves(sourceSetup.getMoves());
            var signature = F2LCaseSignatureExtractor.extract(setupCube.cubeState(), insertSlot, setupCube.orientation());
            var caseName = insertAuf.isEmpty() ? name : name + "-before-" + insertAuf;
            setupCases.add(new F2LSetupCase(insertSlot, mask, signature, alg, sourceSetup, caseName));
        }

        return List.copyOf(setupCases);
    }

    private static void validateSeedCase(F2LSetupCase setupCase) {
        var source = new OrientedCube();
        source.applyMoves(setupCase.sourceSetup().getMoves());
        var sourceOrientation = source.orientation();
        var targetPair = F2LGeometry.targetSlotFor(setupCase.insertSlot(), sourceOrientation);
        var preservedTargets = new ArrayList<F2LGeometry.TargetSlot>();

        if (!F2LGeometry.isTargetCrossSolved(source.cubeState(), F2LGeometry.targetCrossForOrientation(sourceOrientation))) {
            throw new IllegalArgumentException("F2L setup case source does not preserve cross: " + setupCase.name());
        }
        for (var preservedSlot : setupCase.preservedSlots().slots()) {
            var target = F2LGeometry.targetSlotFor(preservedSlot, sourceOrientation);
            if (!F2LGeometry.isTargetSlotSolved(source.cubeState(), target)) {
                throw new IllegalArgumentException("F2L setup case source does not preserve slot " + preservedSlot + ": " + setupCase.name());
            }
            preservedTargets.add(target);
        }

        source.applyMoves(setupCase.algorithm().getMoves());

        var orientation = source.orientation();
        if (!F2LGeometry.isTargetCrossSolved(source.cubeState(), F2LGeometry.targetCrossForOrientation(orientation))) {
            throw new IllegalArgumentException("F2L setup case does not preserve cross: " + setupCase.name());
        }

        if (!F2LGeometry.isPairConnected(
                source.cubeState(),
                new F2LGeometry.SlotPair(targetPair.corner(), targetPair.edge()),
                orientation
        )) {
            throw new IllegalArgumentException("F2L setup case does not connect target pair: " + setupCase.name());
        }

        for (var target : preservedTargets) {
            if (!F2LGeometry.isTargetSlotSolved(source.cubeState(), target)) {
                throw new IllegalArgumentException("F2L setup case does not preserve slot " + target + ": " + setupCase.name());
            }
        }
    }

    private static Algorithm canonicalConnectedReference(F2LSlot insertSlot) {
        return canonicalInsertAlgorithm(insertSlot).inverse();
    }

    private static Algorithm canonicalInsertAlgorithm(F2LSlot insertSlot) {
        return switch (insertSlot) {
            case FR -> Algorithm.parse("R U' R'");
            case FL -> Algorithm.parse("L' U L");
            case BL -> Algorithm.parse("L U' L'");
            case BR -> Algorithm.parse("R' U R");
        };
    }

    private static final class SeedCaseList {
        private final List<F2LSetupCase> cases = new ArrayList<>();

        private void add(String algorithm, F2LSlot insertSlot, List<F2LSlot> preservedSlots, String name) {
            cases.addAll(casesFromAlgorithm(algorithm, insertSlot, preservedSlots, name));
        }

        private List<F2LSetupCase> toList() {
            return List.copyOf(cases);
        }
    }
}
