# CFOP Cube Solver

Java 17 Rubik's Cube solver and experimentation project built around CFOP stages:

- Cross
- F2L
- OLL
- PLL

The project includes:

- a cubie-level cube model
- parsing and execution for standard notation, slice moves, cube rotations, and lowercase wide moves
- selected cross-face support
- seeded F2L, OLL, and PLL case databases
- analyzers and tests for the current solving pipeline

## Current Status

What works today:

- cube state representation and move execution
- face turns, slice moves, cube rotations, and wide moves like `r`, `u`, `f`, `l`, `b`, `d`
- cross solving on arbitrary selected cross faces
- F2L solving with a hybrid approach:
  - seeded database cases
  - validation by direct execution
  - search fallback when no DB candidate works
- OLL solving from a seeded OLL database
- PLL solving from a seeded PLL database, including final AUF handling
- frame-based execution using `OrientedCube`, so stage outputs can contain `x/y/z` reorientation moves and still execute consistently

What is still incomplete:

- there is no full CFOP orchestration class yet; `SolverMain` wires the current stages for development

Signature collisions are surfaced explicitly:

- `SolverMain` prints collision reports for F2L, OLL, and PLL seeds to make that visible
- F2L validates DB candidates by execution before accepting them
- OLL and PLL validate selected DB algorithms by execution before accepting them

## Requirements

- Java 17
- Maven 3.9+

## Build

```bash
mvn clean compile
```

## Run Tests

```bash
mvn test
```

## Run The Demo Main

```bash
mvn clean compile exec:java -Dexec.mainClass=solver.SolverMain
```

You can also pass a selected cross face:

```bash
mvn clean compile exec:java -Dexec.mainClass=solver.SolverMain -Dexec.args="U"
```

Supported face arguments are the normal face letters:

- `D`
- `U`
- `F`
- `B`
- `L`
- `R`

## Notation Support

The parser supports:

- face turns: `R U F D L B`
- double turns: `R2`
- inverse turns: `R'`
- slice moves: `M E S`
- cube rotations: `x y z`
- lowercase wide moves:
  - `r u f d l b`
  - and their `2` / prime variants

Examples:

```text
R U R' U'
r U r'
F R U R' U' F'
x y' r U2 r'
```

## Project Layout

### Core cube model

- [`src/main/java/cube/CubeState.java`](src/main/java/cube/CubeState.java)
- [`src/main/java/cube/Move.java`](src/main/java/cube/Move.java)
- [`src/main/java/cube/MoveApplier.java`](src/main/java/cube/MoveApplier.java)
- [`src/main/java/cube/MoveTables.java`](src/main/java/cube/MoveTables.java)
- [`src/main/java/cube/Algorithm.java`](src/main/java/cube/Algorithm.java)

### Frame/orientation model

- [`src/main/java/cube/CubeOrientation.java`](src/main/java/cube/CubeOrientation.java)
- [`src/main/java/cube/OrientedCube.java`](src/main/java/cube/OrientedCube.java)
- [`src/main/java/cube/OrientationFrames.java`](src/main/java/cube/OrientationFrames.java)

This project currently uses a frame model for the runtime solver path:

- `x/y/z` inside solver output are treated as orientation changes in `OrientedCube`
- later moves are interpreted relative to that carried frame

### CFOP analyzers

- [`src/main/java/cfop/CrossAnalyzer.java`](src/main/java/cfop/CrossAnalyzer.java)
- [`src/main/java/cfop/F2LAnalyzer.java`](src/main/java/cfop/F2LAnalyzer.java)
- [`src/main/java/cfop/OLLAnalyzer.java`](src/main/java/cfop/OLLAnalyzer.java)
- [`src/main/java/cfop/PLLAnalyzer.java`](src/main/java/cfop/PLLAnalyzer.java)

### Case databases

- [`src/main/java/algorithms/F2LCaseDatabase.java`](src/main/java/algorithms/F2LCaseDatabase.java)
- [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)
- [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

### Solvers

- [`src/main/java/solver/CrossSolver.java`](src/main/java/solver/CrossSolver.java)
- [`src/main/java/solver/F2LSolver.java`](src/main/java/solver/F2LSolver.java)
- [`src/main/java/solver/OLLSolver.java`](src/main/java/solver/OLLSolver.java)
- [`src/main/java/solver/PLLSolver.java`](src/main/java/solver/PLLSolver.java)
- [`src/main/java/solver/SolverMain.java`](src/main/java/solver/SolverMain.java)

## How F2L Works Right Now

F2L uses a slot-aware DB key:

```text
(F2LSlot, F2LCaseSignature)
```

Runtime flow:

1. tries exact DB lookup for each unsolved pair under the configured prefix set
2. validates the matched candidate by execution
3. accepts it only if it:
   - preserves cross
   - preserves already solved pairs
   - solves one new pair
4. falls back to one-slot search if no DB candidate works

Enable concise F2L diagnostics with:

```bash
mvn -q -Df2l.debug=true compile exec:java -Dexec.mainClass=solver.SolverMain
```

For every signature miss/check:

```bash
mvn -q -Df2l.debug=true -Df2l.debug.verbose=true compile exec:java -Dexec.mainClass=solver.SolverMain
```

## How OLL Works Right Now

OLL currently:

1. assumes cross and F2L are already solved
2. tries `""`, `U`, `U2`, `U'`
3. extracts the current OLL signature
4. looks that signature up in the OLL DB
5. validates the resulting algorithm by execution

This works as long as the seeded OLL signature uniquely identifies the case.
`SolverMain` prints OLL seed collisions so they are easy to catch while editing the DB.

## How PLL Works Right Now

PLL currently:

1. assumes cross, F2L, and OLL are already solved
2. tries `""`, `U`, `U2`, `U'`
3. returns that AUF immediately if it solves the cube
4. extracts the current PLL permutation signature
5. looks that signature up in the PLL DB
6. validates the resulting algorithm plus optional final AUF by execution

The PLL signature records which logical last-layer corner and edge pieces occupy the four logical U-layer corner and edge positions.
The seeded PLL DB stores 21 base PLL algorithms under 84 signatures, covering the four possible final AUF states for each algorithm.

## Adding Cases

### F2L

Edit:

- [`src/main/java/algorithms/F2LCaseDatabase.java`](src/main/java/algorithms/F2LCaseDatabase.java)

F2L cases are currently seeded in `seedBasicCaseList()`.

### OLL

Edit:

- [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)

OLL cases are currently seeded in `seedCaseList()`.

### PLL

Edit:

- [`src/main/java/algorithms/PLLCaseDatabase.java`](src/main/java/algorithms/PLLCaseDatabase.java)

PLL cases are currently seeded in `seedCaseList()`.

## Useful Commands

Run only the OLL tests:

```bash
mvn -q -Dtest=test.OLLAnalyzerTest,test.OLLSolverTest test
```

Run only the PLL tests:

```bash
mvn -q -Dtest=test.PLLAnalyzerTest,test.PLLCaseDatabaseTest,test.PLLSolverTest test
```

Run only the F2L tests:

```bash
mvn -q -Dtest=test.F2LSolverTest test
```

Run orientation / move-layer tests:

```bash
mvn -q -Dtest=test.AlgorithmTest,test.MoveApplierTest,test.OrientedCubeTest test
```

## Known Limitations

- F2L, OLL, and PLL databases are hand-seeded and can temporarily contain collisions while cases are being edited
- `SolverMain` is still a developer/demo harness, not a polished CLI

## Next Good Steps

- add a full CFOP orchestration class
- keep tightening F2L/OLL signatures and seed collision tests as the databases grow
