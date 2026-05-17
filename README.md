# CFOP Cube Solver

Java 17 Maven Rubik's Cube solver and experimentation project built around a CFOP pipeline:

- Cross
- F2L
- OLL
- PLL

The cube model is cubie-based (`cornerPerm/cornerOri`, `edgePerm/edgeOri`). Runtime solving uses `OrientedCube` plus `CubeOrientation`, so `x/y/z` rotations are frame changes instead of physical cubie mutations. Solver and analyzer code should stay frame-aware; `MoveApplier.applyMove` is the low-level physical executor.

## Current Status

Implemented:

- face turns, slice moves, cube rotations, and lowercase wide moves
- selected cross-face solving
- default 2-phase F2L solving with setup and insert phase databases
- legacy F2L DB mode for comparison
- OLL solving from seeded sticker-orientation signatures
- PLL solving from seeded last-layer permutation signatures, including final AUF handling
- validation-by-execution after DB lookup in F2L, OLL, and PLL

Known limitations:

- F2L is still experimental and currently has only a small number of setup/insert cases seeded.
- The legacy F2L DB remains available via `-Df2l.legacy=true`, but it has known signature collisions and should not be treated as authoritative.
- There is no standalone CFOP orchestration class yet; `SolverMain` wires the development pipeline.

## Requirements

- Java 17
- Maven 3.9+

## Build And Test

```bash
mvn -q -Dmaven.compiler.useIncrementalCompilation=false test
```

```bash
mvn -q clean compile
```

## Run SolverMain

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain
```

Pass the selected cross face as the first argument:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Dexec.args="U"
```

Pass one or more scrambles after the face. Separate multiple scrambles with semicolons:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Dexec.args="U R D R' D2 R D' R'"
```

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Dexec.args="U R U R'; F R U R' U' F'"
```

Supported cross-face arguments are `D`, `U`, `F`, `B`, `L`, and `R`.

Use the legacy F2L DB path:

```bash
mvn -q clean compile exec:java -Dexec.mainClass=solver.SolverMain -Df2l.legacy=true
```

Enable F2L diagnostics:

```bash
mvn -q -Df2l.debug=true compile exec:java -Dexec.mainClass=solver.SolverMain
```

```bash
mvn -q -Df2l.debug=true -Df2l.debug.verbose=true compile exec:java -Dexec.mainClass=solver.SolverMain
```

## Notation Support

The parser supports:

- face turns: `R U F D L B`
- double turns: `R2`
- inverse turns: `R'`
- slice moves: `M E S`
- cube rotations: `x y z`
- lowercase wide moves: `r u f d l b`

Examples:

```text
R U R' U'
r U r'
F R U R' U' F'
x y' r U2 r'
```

## Project Layout

Core cube model:

- [`src/main/java/cube/CubeState.java`](src/main/java/cube/CubeState.java)
- [`src/main/java/cube/Move.java`](src/main/java/cube/Move.java)
- [`src/main/java/cube/MoveApplier.java`](src/main/java/cube/MoveApplier.java)
- [`src/main/java/cube/MoveTables.java`](src/main/java/cube/MoveTables.java)
- [`src/main/java/cube/Algorithm.java`](src/main/java/cube/Algorithm.java)

Frame/orientation model:

- [`src/main/java/cube/CubeOrientation.java`](src/main/java/cube/CubeOrientation.java)
- [`src/main/java/cube/OrientedCube.java`](src/main/java/cube/OrientedCube.java)
- [`src/main/java/cube/OrientationFrames.java`](src/main/java/cube/OrientationFrames.java)

CFOP analyzers:

- [`src/main/java/cfop/CrossAnalyzer.java`](src/main/java/cfop/CrossAnalyzer.java)
- [`src/main/java/cfop/F2LAnalyzer.java`](src/main/java/cfop/F2LAnalyzer.java)
- [`src/main/java/cfop/OLLAnalyzer.java`](src/main/java/cfop/OLLAnalyzer.java)
- [`src/main/java/cfop/PLLAnalyzer.java`](src/main/java/cfop/PLLAnalyzer.java)

Case databases:

- [`src/main/java/algorithms/F2LSetupCaseDatabase.java`](src/main/java/algorithms/F2LSetupCaseDatabase.java)
- [`src/main/java/algorithms/F2LInsertCaseDatabase.java`](src/main/java/algorithms/F2LInsertCaseDatabase.java)
- [`src/main/java/algorithms/F2LCaseDatabase.java`](src/main/java/algorithms/F2LCaseDatabase.java)
- [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)
- [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

Solvers:

- [`src/main/java/solver/CrossSolver.java`](src/main/java/solver/CrossSolver.java)
- [`src/main/java/solver/F2LSolver.java`](src/main/java/solver/F2LSolver.java)
- [`src/main/java/solver/OLLSolver.java`](src/main/java/solver/OLLSolver.java)
- [`src/main/java/solver/PLLSolver.java`](src/main/java/solver/PLLSolver.java)
- [`src/main/java/solver/SolverMain.java`](src/main/java/solver/SolverMain.java)

## F2L

Default F2L flow:

1. Skip slots already solved after cross.
2. Try insert DB directly with prefixes: no prefix, AUF, and `y/y'/y2` plus AUF.
3. If insert misses, try setup DB with the same prefixes.
4. After setup connects the pair, try insert DB again.
5. Fall back to bounded one-slot search when no phase DB case works.

Setup and insert cases are keyed by:

```text
(insert slot, preservation mask, F2L case signature)
```

`insertSlot` is the visible logical slot in the current frame. `preservedSlots` describes solved slots the algorithm must preserve in that same visible frame.

Add 2-phase F2L cases here:

- setup cases: [`src/main/java/algorithms/F2LSetupCaseDatabase.java`](src/main/java/algorithms/F2LSetupCaseDatabase.java)
- insert cases: [`src/main/java/algorithms/F2LInsertCaseDatabase.java`](src/main/java/algorithms/F2LInsertCaseDatabase.java)

For setup cases, the seed algorithm should connect the pair while preserving the listed slots. The database builds source signatures from canonical connected-pair references and insert AUF variants.

For insert cases, the seed algorithm should insert the connected pair into the target slot while preserving the listed slots. The database seeds by applying the inverse algorithm to a solved cube and extracting the signature.

## OLL And PLL

OLL signatures are sticker-orientation patterns, not cubie permutations. OLL lookup tries `""`, `U`, `U2`, and `U'`, then validates the candidate by execution.

PLL signatures track the logical last-layer corner and edge pieces occupying the four logical U-layer positions. PLL seeds each algorithm under the four possible final-AUF setup signatures, then the solver validates with possible post-AUF.

Add cases here:

- OLL: [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)
- PLL: [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

## Useful Targeted Tests

```bash
mvn -q -Dtest=test.F2LPhaseCaseDatabaseTest test
```

```bash
mvn -q -Dtest=test.OLLAnalyzerTest,test.OLLSolverTest test
```

```bash
mvn -q -Dtest=test.PLLAnalyzerTest,test.PLLCaseDatabaseTest,test.PLLSolverTest test
```
