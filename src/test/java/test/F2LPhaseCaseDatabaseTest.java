package test;

import algorithms.F2LInsertCaseDatabase;
import algorithms.F2LSetupCaseDatabase;
import cfop.F2LCaseSignatureExtractor;
import cfop.F2LGeometry;
import cfop.F2LPreservationMask;
import cfop.F2LSlot;
import cube.Algorithm;
import cube.CubeState;
import cube.Face;
import cube.MoveApplier;
import cube.OrientedCube;
import org.junit.jupiter.api.Test;
import solver.CrossSolver;
import solver.F2LSolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class F2LPhaseCaseDatabaseTest {
    @Test
    void preservationMask_shouldAcceptSupersetPreservation() {
        var required = F2LPreservationMask.of(List.of(F2LSlot.FR));
        var candidate = F2LPreservationMask.of(List.of(F2LSlot.FR, F2LSlot.BL));

        assertTrue(candidate.preservesAll(required));
    }

    @Test
    void insertDatabase_shouldSeedCaseFromInverseAlgorithm() {
        var database = F2LInsertCaseDatabase.empty();
        database.register("R U' R'", F2LSlot.FR, List.of(), "fr-insert");

        var source = new OrientedCube();
        source.applyMoves(Algorithm.parse("R U' R'").inverse().getMoves());
        var signature = F2LCaseSignatureExtractor.extract(source.cubeState(), F2LSlot.FR, source.orientation());

        var match = database.find(F2LSlot.FR, F2LPreservationMask.empty(), signature);

        assertTrue(match.isPresent());
        assertEquals("fr-insert", match.get().name());
    }

    @Test
    void setupDatabase_shouldSeedCaseFromConnectedReference() {
        var database = F2LSetupCaseDatabase.empty();
        database.register("R U R'", F2LSlot.FR, List.of(), "fr-setup");

        var source = new OrientedCube();
        source.applyAlgorithm("R U R' U2 R U' R'");
        var signature = F2LCaseSignatureExtractor.extract(source.cubeState(), F2LSlot.FR, source.orientation());

        var match = database.find(F2LSlot.FR, F2LPreservationMask.empty(), signature);

        assertTrue(match.isPresent());
        assertEquals("fr-setup-before-U2", match.get().name());
    }

    @Test
    void solver_shouldUsePhaseDatabasesWhenCasesAreSeeded() {
        var insertDatabase = F2LInsertCaseDatabase.empty();
        insertDatabase.register("R U' R'", F2LSlot.FR, List.of(), "fr-insert");

        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, Algorithm.parse("R U' R'").inverse().toString());

        var solution = new F2LSolver(F2LSetupCaseDatabase.empty(), insertDatabase).solveSlot(cube, F2LSlot.FR);

        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(F2LGeometry.isTargetSlotSolved(cube, F2LGeometry.targetSlotFor(F2LSlot.FR, new cube.CubeOrientation())));
    }

    @Test
    void solver_shouldTryPrefixBeforeInsertDatabaseLookup() {
        var insertDatabase = F2LInsertCaseDatabase.empty();
        insertDatabase.register("R U' R'", F2LSlot.FR, List.of(), "fr-insert");

        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, Algorithm.parse("U R U' R'").inverse().toString());

        var solution = new F2LSolver(F2LSetupCaseDatabase.empty(), insertDatabase).solveSlot(cube, F2LSlot.FR);

        assertEquals("U R U' R'", solution.toString());
        MoveApplier.executeMoves(cube, solution.getMoves());
        assertTrue(F2LGeometry.isTargetSlotSolved(cube, F2LGeometry.targetSlotFor(F2LSlot.FR, new cube.CubeOrientation())));
    }

    @Test
    void solver_shouldUsePrefixedSetupThenPrefixedInsertForThreeSolvedSlots() {
        var cube = new CubeState();
        MoveApplier.applyAlgorithm(cube, "R D R' D2 R D' R'");
        var orientedCube = new OrientedCube(cube);
        orientedCube.applyMoves(new CrossSolver().solve(orientedCube.cubeState(), Face.U).getMoves());

        var solution = new F2LSolver(F2LSetupCaseDatabase.seedCases(), F2LInsertCaseDatabase.seedCases())
                .solve(orientedCube);

        assertEquals("y2 R U R' U2 R U' R'", solution.toString());
        orientedCube.applyMoves(solution.getMoves());
        assertTrue(cfop.F2LAnalyzer.isF2LSolved(orientedCube.cubeState(), orientedCube.orientation()));
    }
}
