# CFOP Cube Solver

Java 17 Rubik's Cube solver and experimentation project built around CFOP stages:

- Cross
- F2L
- OLL
- PLL is not implemented yet

The project includes:

- a cubie-level cube model
- parsing and execution for standard notation, slice moves, cube rotations, and lowercase wide moves
- selected cross-face support
- seeded F2L and OLL case databases
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
- frame-based execution using `OrientedCube`, so stage outputs can contain `x/y/z` reorientation moves and still execute consistently

What is still incomplete:

- PLL is not implemented
- `CfopSolver` is still a placeholder
- F2L and OLL signature extractors currently have known signature-collision issues for some distinct cases

That last point matters:

- F2L runtime does not depend on exact signature matching anymore, so it still works
- OLL currently still uses signature lookup, so if two real cases collapse to the same signature, whichever one is kept by the DB map wins
- `SolverMain` prints collision reports for both F2L and OLL seeds to make that visible

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

### Case databases

- [`src/main/java/algorithms/F2LCaseDatabase.java`](src/main/java/algorithms/F2LCaseDatabase.java)
- [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)

### Solvers

- [`src/main/java/solver/CrossSolver.java`](src/main/java/solver/CrossSolver.java)
- [`src/main/java/solver/F2LSolver.java`](src/main/java/solver/F2LSolver.java)
- [`src/main/java/solver/OLLSolver.java`](src/main/java/solver/OLLSolver.java)
- [`src/main/java/solver/SolverMain.java`](src/main/java/solver/SolverMain.java)

## How F2L Works Right Now

F2L currently does not pick a DB algorithm by exact signature lookup.

Instead it:

1. tries seeded F2L DB algorithms under a small prefix set
2. executes them on a trial cube in the current frame
3. validates whether they:
   - preserve cross
   - preserve already solved pairs
   - solve one new pair
4. falls back to one-slot search if no DB candidate works

This makes the F2L runtime more robust even while the F2L signature extractor still has collisions.

## How OLL Works Right Now

OLL currently:

1. assumes cross and F2L are already solved
2. tries `""`, `U`, `U2`, `U'`
3. extracts the current OLL signature
4. looks that signature up in the OLL DB
5. validates the resulting algorithm by execution

This works as long as the seeded OLL signature uniquely identifies the case. Right now some distinct seeded OLL cases collide, so this is one of the main known limitations.

## Adding Cases

### F2L

Edit:

- [`src/main/java/algorithms/F2LCaseDatabase.java`](src/main/java/algorithms/F2LCaseDatabase.java)

F2L cases are currently seeded in `seedBasicCaseList()`.

### OLL

Edit:

- [`src/main/java/algorithms/OLLCaseDatabase.java`](src/main/java/algorithms/OLLCaseDatabase.java)

OLL cases are currently seeded in `seedCaseList()`.

## Useful Commands

Run only the OLL tests:

```bash
mvn -q -Dtest=test.OLLAnalyzerTest,test.OLLSolverTest test
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

- `CfopSolver` is not wired yet
- PLL is unimplemented
- OLL DB signatures currently collapse some distinct cases
- F2L case signatures also collide for some distinct registrations, though F2L runtime is no longer blocked by that
- `SolverMain` is still a developer/demo harness, not a polished CLI

## Next Good Steps

- fix OLL signature extraction so distinct OLL cases no longer collide
- decide whether to make OLL runtime collision-aware before or instead of strengthening the signature
- implement PLL
- wire the full pipeline into `CfopSolver`
