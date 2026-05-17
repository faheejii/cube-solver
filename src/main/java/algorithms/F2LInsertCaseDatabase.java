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

public class F2LInsertCaseDatabase {
    private final Map<F2LInsertCaseKey, F2LInsertCase> cases = new LinkedHashMap<>();

    public static F2LInsertCaseDatabase empty() {
        return new F2LInsertCaseDatabase();
    }

    public static F2LInsertCaseDatabase seedCases() {
        var database = new F2LInsertCaseDatabase();
        for (var insertCase : seedCaseList()) {
            database.register(insertCase);
        }
        return database;
    }

    private static List<F2LInsertCase> seedCaseList() {
        var cases = new SeedCaseList();
        var fr = F2LSlot.FR;
        var br = F2LSlot.BR;
        var fl = F2LSlot.FL;
        var bl = F2LSlot.BL;

        // Add insert cases here. Each algorithm should insert into insertSlot while preserving the listed slots.
        cases.add("R U' R'", fr, List.of(br, fl, bl), "case-1.1");
        cases.add("R U' R'", fr, List.of(br), "case-1.2");
        cases.add("R U' R'", fr, List.of(br, fl), "case-1.3");
        cases.add("R U' R'", fr, List.of(br, bl), "case-1.4");
        cases.add("R U' R'", fr, List.of(fl), "case-1.5");
        cases.add("R U' R'", fr, List.of(fl, bl), "case-1.6");
        cases.add("R U' R'", fr, List.of(bl), "case-1.7");

        return cases.toList();
    }

    public void register(String algorithm, F2LSlot insertSlot, Collection<F2LSlot> preservedSlots, String name) {
        register(caseFromAlgorithm(algorithm, insertSlot, preservedSlots, name));
    }

    public void register(F2LInsertCase insertCase) {
        if (insertCase == null) {
            throw new IllegalArgumentException("insertCase cannot be null");
        }
        if (cases.containsKey(insertCase.key())) {
            throw new IllegalArgumentException("Duplicate F2L insert case key: " + insertCase.key());
        }
        cases.put(insertCase.key(), insertCase);
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

        var matches = new ArrayList<F2LInsertCase>();
        for (var insertCase : cases.values()) {
            if (insertCase.insertSlot() == insertSlot
                    && insertCase.signature().equals(signature)
                    && insertCase.preservedSlots().preservesAll(requiredPreservedSlots)) {
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
        for (var insertCase : cases.values()) {
            validateSeedCase(insertCase);
        }
    }

    private static F2LInsertCase caseFromAlgorithm(
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
        var setupCube = new OrientedCube();
        setupCube.applyMoves(alg.inverse().getMoves());
        var signature = F2LCaseSignatureExtractor.extract(setupCube.cubeState(), insertSlot, setupCube.orientation());
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
        if (!F2LGeometry.isPairConnected(
                source.cubeState(),
                new F2LGeometry.SlotPair(insertedTarget.corner(), insertedTarget.edge()),
                sourceOrientation
        )) {
            throw new IllegalArgumentException("F2L insert case source is not a connected target pair: " + insertCase.name());
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

    private static final class SeedCaseList {
        private final List<F2LInsertCase> cases = new ArrayList<>();

        private void add(String algorithm, F2LSlot insertSlot, List<F2LSlot> preservedSlots, String name) {
            cases.add(caseFromAlgorithm(algorithm, insertSlot, preservedSlots, name));
        }

        private List<F2LInsertCase> toList() {
            return List.copyOf(cases);
        }
    }
}
